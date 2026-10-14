package com.garage.garageapi.integration.dsers;

import java.util.Optional;

public interface DsersOrderGateway {
    Result submit(DsersOrderSubmission submission);
    Optional<Result> findByExternalReference(String externalReference);

    record Result(String supplierOrderId, String supplierShipmentOrderId,
                  String externalReference, String status) { }
}
