package com.garage.garageapi.integration.dsers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimulatedDsersOrderGateway implements DsersOrderGateway {
    private final ConcurrentHashMap<String, Result> orders = new ConcurrentHashMap<>();
    private final boolean enabled;

    public SimulatedDsersOrderGateway(@Value("${dsers.simulation.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Result submit(DsersOrderSubmission submission) {
        requireEnabled();
        return orders.computeIfAbsent(submission.externalReference(), reference -> new Result(
                "DSERS-SIM-" + reference, "DSERS-SIM-SHIP-" + reference,
                reference, "SIMULATED_CREATED"));
    }

    @Override
    public Optional<Result> findByExternalReference(String externalReference) {
        requireEnabled();
        return Optional.ofNullable(orders.get(externalReference));
    }

    private void requireEnabled() {
        if (!enabled) throw new IllegalStateException(
                "Integração DSers real indisponível; habilite apenas a simulação local explicitamente");
    }
}
