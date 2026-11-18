package com.garage.garageapi.integration.aliexpress;

import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.web.client.RestClientResponseException;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class AliExpressPreviewTransportTests {
    static final URI ENDPOINT=URI.create("https://api-sg.aliexpress.com/sync");
    static final byte[] FORM="method=aliexpress.ds.product.get&session=synthetic-token&sign=synthetic-signature".getBytes(StandardCharsets.UTF_8);
    CloseableHttpClient primary=mock(CloseableHttpClient.class),regional=mock(CloseableHttpClient.class);

    @SuppressWarnings("unchecked")
    void response(CloseableHttpClient client,int status) throws Exception {
        response(client,status,ENDPOINT);
    }
    @SuppressWarnings("unchecked")
    void response(CloseableHttpClient client,int status,URI endpoint) throws Exception {
        when(client.execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class))).thenAnswer(invocation->{
            ClassicHttpRequest request=invocation.getArgument(0);
            assertThat(request.getUri()).isEqualTo(endpoint);
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getEntity().getContent().readAllBytes()).isEqualTo(FORM);
            var response=new BasicClassicHttpResponse(status);
            response.setEntity(new StringEntity("{\"rsp_code\":200,\"result\":{\"product_id\":\"1005010598638085\"}}",ContentType.APPLICATION_JSON));
            return invocation.getArgument(1,HttpClientResponseHandler.class).handleResponse(response);
        });
    }
    @Test void successfulPrimaryQueryDoesNotUseRegionalRouteOrExposeCredentials(CapturedOutput output) throws Exception {
        response(primary,200);
        try(var transport=new AliExpressPreviewTransport(ENDPOINT,primary,regional,Duration.ofSeconds(2))) {
            assertThat(transport.post(FORM,"1005010598638085").path("rsp_code").asInt()).isEqualTo(200);
            verifyNoInteractions(regional);
        }
        assertThat(output.getAll()).contains("route=primary","outcome=SUCCESS").doesNotContain("synthetic-token","synthetic-signature");
    }
    @Test void connectionTimeoutUsesExactlyOneRegionalAttemptWithSameHostAndBody(CapturedOutput output) throws Exception {
        when(primary.execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class))).thenThrow(new ConnectTimeoutException("synthetic-token"));
        response(regional,200);
        try(var transport=new AliExpressPreviewTransport(ENDPOINT,primary,regional,Duration.ofSeconds(2))) {
            assertThat(transport.post(FORM,"1005010598638085").path("result").path("product_id").asText()).isEqualTo("1005010598638085");
            verify(primary,times(1)).execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class));
            verify(regional,times(1)).execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class));
        }
        assertThat(output.getAll()).contains("route=primary","outcome=CONNECT_FAILURE","route=regional","outcome=SUCCESS")
                .doesNotContain("synthetic-token","synthetic-signature");
    }
    @Test void bothConnectionFailuresStopAfterTwoAttempts() throws Exception {
        when(primary.execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class))).thenThrow(new ConnectTimeoutException("private-primary"));
        when(regional.execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class))).thenThrow(new ConnectTimeoutException("private-regional"));
        try(var transport=new AliExpressPreviewTransport(ENDPOINT,primary,regional,Duration.ofSeconds(2))) {
            assertThatThrownBy(()->transport.post(FORM,"1005010598638085")).isInstanceOf(ConnectTimeoutException.class);
            verify(primary,times(1)).execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class));
            verify(regional,times(1)).execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class));
        }
    }
    @Test void readTimeoutNeverUsesRegionalRoute() throws Exception {
        when(primary.execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class))).thenThrow(new SocketTimeoutException("private-token"));
        try(var transport=new AliExpressPreviewTransport(ENDPOINT,primary,regional,Duration.ofSeconds(2))) {
            assertThatThrownBy(()->transport.post(FORM,"1005010598638085")).isInstanceOf(SocketTimeoutException.class);
            verifyNoInteractions(regional);
        }
    }
    @Test void certificateFailureNeverUsesRegionalRoute() throws Exception {
        when(primary.execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class))).thenThrow(new javax.net.ssl.SSLHandshakeException("certificate rejected"));
        try(var transport=new AliExpressPreviewTransport(ENDPOINT,primary,regional,Duration.ofSeconds(2))) {
            assertThatThrownBy(()->transport.post(FORM,"1005010598638085")).isInstanceOf(javax.net.ssl.SSLHandshakeException.class);
            verifyNoInteractions(regional);
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={302,401,403,429,500,504})
    void httpResponsesNeverTriggerRetryOrRedirect(int status) throws Exception {
        response(primary,status);
        try(var transport=new AliExpressPreviewTransport(ENDPOINT,primary,regional,Duration.ofSeconds(2))) {
            assertThatThrownBy(()->transport.post(FORM,"1005010598638085")).isInstanceOfSatisfying(RestClientResponseException.class,
                    ex->assertThat(ex.getStatusCode().value()).isEqualTo(status));
            verifyNoInteractions(regional);
        }
    }
    @Test void resolverRejectsEveryNonCanonicalHostWithoutDnsQueries() {
        for(boolean regionalRoute:new boolean[]{false,true}) {
            var resolver=AliExpressPreviewTransport.resolver(regionalRoute);
            assertThatThrownBy(()->resolver.resolve("example.test")).isInstanceOf(UnknownHostException.class);
            assertThatThrownBy(()->resolver.resolveCanonicalHostname("example.test")).isInstanceOf(UnknownHostException.class);
            assertThatCode(()->assertThat(resolver.resolveCanonicalHostname("api-sg.aliexpress.com")).isEqualTo("api-sg.aliexpress.com")).doesNotThrowAnyException();
        }
    }
    @Test void deadlineCancelsStalledBodyWithoutRetry() throws Exception {
        var calls=new AtomicInteger();var release=new CountDownLatch(1);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/sync",exchange->{
            calls.incrementAndGet();exchange.sendResponseHeaders(200,10000);
            try(var body=exchange.getResponseBody()) {body.write("{\"rsp_code\":200,\"result\":".getBytes(StandardCharsets.UTF_8));body.flush();release.await(3,TimeUnit.SECONDS);}
            catch(InterruptedException ex){Thread.currentThread().interrupt();}
        });server.start();
        var real=HttpClients.custom().disableAutomaticRetries().disableRedirectHandling().build();
        try(var transport=new AliExpressPreviewTransport(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/sync"),real,regional,Duration.ofMillis(300))) {
            long start=System.nanoTime();
            assertThatThrownBy(()->transport.post(FORM,"1005010598638085")).isInstanceOf(HttpTimeoutException.class);
            assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(2));
            assertThat(calls.get()).isEqualTo(1);verifyNoInteractions(regional);
        } finally {release.countDown();server.stop(0);}
    }

    @Test void stalledRealTlsHandshakeUsesRegionalRouteOnce(CapturedOutput output) throws Exception {
        var release=new CountDownLatch(1);
        var worker=Executors.newSingleThreadExecutor();
        try(var listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
            var accepted=worker.submit(()->{try(var socket=listener.accept()){release.await(3,TimeUnit.SECONDS);}return null;});
            URI endpoint=URI.create("https://api-sg.aliexpress.com:"+listener.getLocalPort()+"/sync");
            response(regional,200,endpoint);
            try(var transport=new AliExpressPreviewTransport(endpoint,AliExpressPreviewTransport.buildClient(loopbackResolver(),Duration.ofMillis(250)),regional,Duration.ofSeconds(2))) {
                assertThat(transport.post(FORM,"1005010598638085").path("rsp_code").asInt()).isEqualTo(200);
                verify(regional,times(1)).execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class));
            } finally {release.countDown();}
            accepted.get(2,TimeUnit.SECONDS);
        } finally {release.countDown();worker.shutdownNow();}
        assertThat(output.getAll()).contains("route=primary","outcome=CONNECT_FAILURE","route=regional","outcome=SUCCESS");
    }

    @Test void regionalAttemptReceivesOnlyTheRemainingBudget() throws Exception {
        var release=new CountDownLatch(1);var calls=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/sync",exchange->{
            calls.incrementAndGet();exchange.sendResponseHeaders(200,10000);
            try(var body=exchange.getResponseBody()){body.write('{');body.flush();release.await(3,TimeUnit.SECONDS);}
            catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{exchange.close();}
        });server.start();
        when(primary.execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class))).thenAnswer(invocation->{
            Thread.sleep(350);throw new ConnectTimeoutException("synthetic connection failure");
        });
        try(var transport=new AliExpressPreviewTransport(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/sync"),primary,
                HttpClients.custom().disableAutomaticRetries().build(),Duration.ofMillis(700))) {
            long start=System.nanoTime();
            assertThatThrownBy(()->transport.post(FORM,"1005010598638085")).isInstanceOf(HttpTimeoutException.class);
            assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofMillis(1000));
            assertThat(calls.get()).isEqualTo(1);
            verify(primary,times(1)).execute(any(ClassicHttpRequest.class),any(HttpClientResponseHandler.class));
        } finally {release.countDown();server.stop(0);}
    }

    @Test void dnsReturningAfterDeadlineCannotSendLateHttpRequestOrRetry() throws Exception {
        var calls=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/sync",exchange->{calls.incrementAndGet();exchange.sendResponseHeaders(200,-1);exchange.close();});server.start();
        DnsResolver delayed=new DnsResolver() {
            public InetAddress[] resolve(String host) throws UnknownHostException {
                try{Thread.sleep(450);}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new UnknownHostException("Interrupted");}
                return new InetAddress[]{InetAddress.getLoopbackAddress()};
            }
            public String resolveCanonicalHostname(String host){return host;}
        };
        try(var transport=new AliExpressPreviewTransport(URI.create("http://api-sg.aliexpress.com:"+server.getAddress().getPort()+"/sync"),
                AliExpressPreviewTransport.buildClient(delayed,Duration.ofMillis(250)),regional,Duration.ofMillis(150))) {
            assertThatThrownBy(()->transport.post(FORM,"1005010598638085")).isInstanceOf(HttpTimeoutException.class);
            assertThat(calls.get()).isZero();verifyNoInteractions(regional);
        } finally {server.stop(0);}
    }

    @Test void concurrentPreviewsHaveIndependentDeadlines() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var handlers=Executors.newFixedThreadPool(2);var callers=Executors.newFixedThreadPool(2);
        try {
            server.setExecutor(handlers);
            server.createContext("/sync",exchange->{
                boolean slow=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8).equals("slow");
                try {
                    if(slow){entered.countDown();release.await(3,TimeUnit.SECONDS);}else Thread.sleep(300);
                    var body="{\"rsp_code\":200}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200,body.length);try(var stream=exchange.getResponseBody()){stream.write(body);}
                }catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{exchange.close();}
            });server.start();
            try(var transport=new AliExpressPreviewTransport(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/sync"),
                    HttpClients.custom().disableAutomaticRetries().build(),regional,Duration.ofMillis(600))) {
                var slow=callers.submit(()->catchThrowable(()->transport.post("slow".getBytes(StandardCharsets.UTF_8),"1005010598638085")));
                assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();Thread.sleep(400);
                var fast=callers.submit(()->transport.post("fast".getBytes(StandardCharsets.UTF_8),"1005010661577657"));
                assertThat(slow.get(2,TimeUnit.SECONDS)).isInstanceOf(HttpTimeoutException.class);
                assertThat(fast.get(2,TimeUnit.SECONDS).path("rsp_code").asInt()).isEqualTo(200);
                verifyNoInteractions(regional);
            } finally {release.countDown();server.stop(0);}
        } finally {release.countDown();server.stop(0);handlers.shutdownNow();callers.shutdownNow();}
    }

    private static DnsResolver loopbackResolver() {
        return new DnsResolver() {
            public InetAddress[] resolve(String host){return new InetAddress[]{InetAddress.getLoopbackAddress()};}
            public String resolveCanonicalHostname(String host){return host;}
        };
    }
}
