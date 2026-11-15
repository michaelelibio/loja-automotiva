package com.garage.garageapi.admin;

import com.garage.garageapi.payment.reconciliation.PaymentReconciliationStore;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/payments/reconciliation")
public class AdminPaymentReconciliationController {
    private final PaymentReconciliationStore store;
    public AdminPaymentReconciliationController(PaymentReconciliationStore store){this.store=store;}
    @GetMapping public List<PaymentReconciliationStore.Failure> failures(){return store.failures();}
}
