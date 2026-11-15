package com.garage.garageapi.payment.reconciliation;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.email.OrderEmailService;
import com.garage.garageapi.order.entity.*;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.*;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.payment.service.PaymentWebhookService;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="mercadopago.reconciliation.enabled=false")
public class PaymentRecoveryIntegrationTests {
    @Autowired PaymentReconciliationStore store;
    @Autowired PaymentReconciliationRepository schedules;
    @Autowired PaymentWebhookService notifications;
    @Autowired PaymentRepository payments;
    @Autowired OrderRepository orders;
    @Autowired AddressRepository addresses;
    @Autowired UserRepository users;
    @MockitoBean MercadoPagoPaymentSearchClient search;
    @MockitoBean CheckoutProGateway gateway;
    @MockitoBean OrderEmailService email;
    @AfterEach void clean(){schedules.deleteAll();payments.deleteAll();orders.deleteAll();addresses.deleteAll();users.deleteAll();}
    @Test void lostWebhookIsRecoveredOnceThroughAuthenticatedLookupAndPersistedUpdater() {
        var p=fixture();when(search.findIds(p.getExternalReference())).thenReturn(List.of("80001"));
        when(gateway.findPayment("80001")).thenReturn(result(p,"80001",new BigDecimal("50"),"BRL"));
        worker().reconcile(p.getId());worker().reconcile(p.getId());
        assertThat(payments.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(orders.findById(p.getOrder().getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        verify(gateway,times(1)).findPayment("80001");
        verify(gateway,never()).createPreference(any());
        verify(email,times(1)).sendPaymentApproved(any());
    }
    @Test void wrongAmountFromSearchResultCannotApproveOrder() {
        var p=fixture();when(search.findIds(p.getExternalReference())).thenReturn(List.of("80002"));
        when(gateway.findPayment("80002")).thenReturn(result(p,"80002",new BigDecimal("0.01"),"BRL"));
        worker().reconcile(p.getId());
        assertThat(payments.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(orders.findById(p.getOrder().getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
    @Test void multipleProviderPaymentsRequireReviewWithoutApplyingAnySnapshot() {
        var p=fixture();when(search.findIds(p.getExternalReference())).thenReturn(List.of("80003","80004"));
        worker().reconcile(p.getId());
        verifyNoInteractions(gateway);
        assertThat(store.failures()).singleElement().satisfies(f->{
            assertThat(f.code()).isEqualTo("AMBIGUOUS_PROVIDER_PAYMENTS");assertThat(f.reviewRequired()).isTrue();
        });
        assertThat(store.claim(p.getId(),Instant.now().plusSeconds(86400))).isNull();
    }
    @Test void failedLookupBacksOffAndSurvivesNewWorkerInstance() {
        var p=fixture();when(search.findIds(p.getExternalReference())).thenThrow(new IllegalStateException("synthetic failure"));
        worker().reconcile(p.getId());
        assertThat(store.failures()).singleElement().satisfies(f->{
            assertThat(f.code()).isEqualTo("PROVIDER_LOOKUP_FAILED");assertThat(f.failureCount()).isEqualTo(1);
        });
        assertThat(store.claim(p.getId(),Instant.now())).isNull();
        // A later process can reclaim persisted scheduling state; no in-memory retry list is required.
        var claim=store.claim(p.getId(),Instant.now().plusSeconds(61));assertThat(claim).isNotNull();
        when(search.findIds(p.getExternalReference())).thenReturn(List.of("80005"));
        when(gateway.findPayment("80005")).thenReturn(result(p,"80005",new BigDecimal("50"),"BRL"));
        // Model a process exit after claiming, then lease expiry in another process.
        assertThat(store.claim(p.getId(),Instant.now().plusSeconds(62))).isNull();
        assertThat(store.claim(p.getId(),Instant.now().plusSeconds(362))).isNotNull();
    }
    @Test void expiredLeaseCanBeRecoveredButOldOwnerCannotOverwriteNewSchedule() {
        var p=fixture();Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);var first=store.claim(p.getId(),now);
        assertThat(first).isNotNull();assertThat(store.claim(p.getId(),now)).isNull();
        var second=store.claim(p.getId(),now.plusSeconds(301));assertThat(second).isNotNull();
        store.complete(first,now.plusSeconds(302),"PROVIDER_LOOKUP_FAILED",false);
        assertThat(schedules.findById(p.getId()).orElseThrow().getLastFailureCode()).isNull();
        store.complete(second,now.plusSeconds(303),null,false);
        assertThat(schedules.findById(p.getId()).orElseThrow().getLastCheckedAt()).isEqualTo(now.plusSeconds(303));
    }
    private PaymentReconciliationWorker worker(){return new PaymentReconciliationWorker(store,search,notifications,true,20);}
    private CheckoutProGateway.PaymentResult result(Payment p,String id,BigDecimal amount,String currency){
        return new CheckoutProGateway.PaymentResult(id,p.getExternalReference(),PaymentStatus.PAID,"accredited",amount,
                currency,"bank_transfer","pix",Instant.now(),true,BigDecimal.ZERO);
    }
    private Payment fixture(){
        var user=users.save(User.local("Recovery fixture",java.util.UUID.randomUUID()+"@example.invalid","synthetic-password-hash"));
        var address=addresses.save(new Address(user,"Fixture","Test recipient","01001000","Test street","1",null,"Test district","Sao Paulo","SP",true));
        var order=orders.saveAndFlush(new Order(user,address,new BigDecimal("50"),BigDecimal.ZERO,Duration.ofHours(24)));
        var payment=payments.saveAndFlush(new Payment(order,PaymentMethod.MERCADO_PAGO));
        payment.applyCheckoutPreference("PREF-"+payment.getId(),"garage_order_"+order.getId()+"_payment_"+payment.getId(),"https://example.invalid");
        return payments.saveAndFlush(payment);
    }
}
