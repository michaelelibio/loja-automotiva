package com.garage.garageapi.integration.aliexpress;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only preview/import transport. Dispatch continues using ProductClient.get. */
@Component
public class AliExpressPreviewTransport implements AutoCloseable {
    static final String HOST = "api-sg.aliexpress.com";
    // Provider-owned CNAME in the canonical gateway's healthy Brazil/US-East DNS chain.
    static final String REGIONAL_DNS = "use.ingress-ae.aliexpress.com";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AliExpressPreviewTransport.class);
    private final CloseableHttpClient primary, regional;
    private final URI endpoint;
    private final Duration budget;
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1, task -> {
        var thread = new Thread(task, "aliexpress-preview-deadline"); thread.setDaemon(true); return thread;
    });

    public AliExpressPreviewTransport() {
        this(URI.create("https://" + HOST + "/sync"), buildClient(false, Duration.ofSeconds(3)),
                buildClient(true, Duration.ofSeconds(3)), Duration.ofSeconds(20));
    }

    AliExpressPreviewTransport(URI endpoint, CloseableHttpClient primary, CloseableHttpClient regional, Duration budget) {
        this.endpoint=endpoint; this.primary=primary; this.regional=regional; this.budget=budget;
        deadlines.setRemoveOnCancelPolicy(true);
    }

    JsonNode post(byte[] form, String productId) throws IOException {
        long deadline=System.nanoTime()+budget.toNanos();
        for(int attempt=0;attempt<2;attempt++) {
            long remaining=deadline-System.nanoTime();
            if(remaining<=0)throw new HttpTimeoutException("Preview deadline exceeded");
            var request=new HttpPost(endpoint);
            request.setHeader("Accept","application/json");
            request.setEntity(new ByteArrayEntity(form, ContentType.APPLICATION_FORM_URLENCODED));
            request.setConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofMilliseconds(Math.max(1,TimeUnit.NANOSECONDS.toMillis(remaining))))
                    .setResponseTimeout(Timeout.ofMilliseconds(Math.max(1,TimeUnit.NANOSECONDS.toMillis(remaining)))).build());
            var expired=new AtomicBoolean();
            var cancellation=deadlines.schedule(()->{expired.set(true);request.cancel();},remaining,TimeUnit.NANOSECONDS);
            long started=System.nanoTime();String route=attempt==0?"primary":"regional";
            try {
                JsonNode result=(attempt==0?primary:regional).execute(request,response->{
                    int status=response.getCode();
                    if(status<200||status>=300)throw new RestClientResponseException("AliExpress HTTP response",status,"",null,new byte[0],null);
                    if(response.getEntity()==null)return null;
                    try(var stream=response.getEntity().getContent()) { return JSON.readTree(stream); }
                });
                if(expired.get()||System.nanoTime()>=deadline)throw new HttpTimeoutException("Preview deadline exceeded");
                timing(productId,route,started,"SUCCESS");return result;
            } catch(IOException ex) {
                boolean timeout=expired.get()||System.nanoTime()>=deadline;
                boolean connection=connectionFailure(ex);
                timing(productId,route,started,timeout?"DEADLINE":connection?"CONNECT_FAILURE":"IO_FAILURE");
                if(timeout)throw new HttpTimeoutException("Preview deadline exceeded");
                // At most one regional connection attempt. No retry for read timeouts or provider errors.
                if(attempt==0&&connection)continue;
                throw ex;
            } catch(RestClientResponseException ex) {
                timing(productId,route,started,"HTTP_"+ex.getStatusCode().value());throw ex;
            } catch(RuntimeException ex) {
                if(expired.get()||System.nanoTime()>=deadline) {
                    timing(productId,route,started,"DEADLINE");throw new HttpTimeoutException("Preview deadline exceeded");
                }
                timing(productId,route,started,"INVALID_RESPONSE");throw ex;
            } finally { cancellation.cancel(false); }
        }
        throw new IOException("AliExpress connection unavailable");
    }

    static boolean connectionFailure(Throwable error) {
        for(Throwable cause=error;cause!=null;cause=cause.getCause())
            if(cause instanceof ConnectTimeoutException||cause instanceof ConnectException||cause instanceof UnknownHostException)return true;
        return false;
    }

    private static void timing(String product,String route,long start,String outcome) {
        LOG.info("AliExpress preview stage; previewId={}; productId={}; stage=product_get; route={}; elapsedMs={}; outcome={}",
                MDC.get("aliexpressPreviewId"),product,route,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),outcome);
    }

    static CloseableHttpClient buildClient(boolean regional,Duration connectTimeout) {
        return buildClient(resolver(regional),connectTimeout);
    }

    static CloseableHttpClient buildClient(DnsResolver dns,Duration connectTimeout) {
        var manager=PoolingHttpClientConnectionManagerBuilder.create().setMaxConnTotal(20).setMaxConnPerRoute(10)
                .setDnsResolver(dns)
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                        .setSocketTimeout(Timeout.ofSeconds(20)).build())
                .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis())).build()).build();
        // Default certificate trust, hostname verification and TLS remain enabled. The URL/Host/SNI never change.
        return HttpClients.custom().setConnectionManager(manager).disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build();
    }

    static DnsResolver resolver(boolean regional) {
        return new DnsResolver() {
            public InetAddress[] resolve(String host) throws UnknownHostException {
                if(!HOST.equalsIgnoreCase(host))throw new UnknownHostException("Unexpected preview host");
                long started=System.nanoTime();
                try {
                    var addresses=InetAddress.getAllByName(regional?REGIONAL_DNS:HOST);
                    return new InetAddress[]{addresses[0]}; // One address per route; follow provider DNS without pinning an IP.
                } finally {
                    LOG.info("AliExpress preview stage; previewId={}; stage=dns; route={}; elapsedMs={}",
                            MDC.get("aliexpressPreviewId"),regional?"regional":"primary",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
                }
            }
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                if(!HOST.equalsIgnoreCase(host))throw new UnknownHostException("Unexpected preview host");return HOST;
            }
        };
    }

    @Override @PreDestroy public void close() throws IOException {
        deadlines.shutdownNow();try{primary.close();}finally{regional.close();}
    }
}
