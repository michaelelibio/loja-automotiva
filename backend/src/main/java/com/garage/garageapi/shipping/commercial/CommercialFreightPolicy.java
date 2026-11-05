package com.garage.garageapi.shipping.commercial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import com.garage.garageapi.shared.exception.ResourceConflictException;

/** Merchant commitments in BRL, not interpretations of AliExpress diagnostic scalars. */
public final class CommercialFreightPolicy {
    private CommercialFreightPolicy(){}
    public enum ParcelSize { SMALL, MEDIUM, LARGE }
    public enum Grouping { PER_SKU, CONSOLIDATED }
    public enum Source { ALIEXPRESS_PORTAL, OFFICIAL_SUPPORT, PAYMENT_PROVIDER_PLAN, DIAGNOSTIC_ONLY }
    public record Evidence(@NotNull Source source,@NotBlank @Pattern(regexp="[A-Za-z0-9_.:/-]{1,150}") String reference,
            @NotNull Instant verifiedAt,@NotNull Instant validUntil,boolean monetaryScaleConfirmed,
            boolean costsAndCoverageChecked) {
        public void requireValid(Instant now) {
            require(source!=Source.DIAGNOSTIC_ONLY && monetaryScaleConfirmed && costsAndCoverageChecked
                && !verifiedAt.isAfter(now) && verifiedAt.isBefore(validUntil) && now.isBefore(validUntil),"Evidência comercial ausente, ambígua ou expirada.");
        }
    }
    public record Profile(@NotNull Long productId,@NotNull Long variantId,@NotNull ParcelSize size,
            @NotBlank @Pattern(regexp="[0-9]{1,18}") String supplierProductId,@NotBlank @Pattern(regexp="[0-9]{1,30}") String supplierSkuId,
            @NotBlank String supplierStoreId,@NotBlank @Pattern(regexp="[A-Z]{2}") String originCountry,
            @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal supplierProductUnitBrl,
            @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal otherSupplierUnitBrl,
            @NotNull @Valid Evidence evidence) {}
    public record Rate(@NotBlank String key,@NotBlank @Pattern(regexp="[A-Z]{2}") String state,
            @NotBlank String category,@NotNull ParcelSize size,@NotBlank String supplierStoreId,@NotBlank @Pattern(regexp="[A-Z]{2}") String originCountry,
            @NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{1,50}") String logisticsCode,@NotBlank @Size(max=120) String logisticsName,
            @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal supplierFreightPerParcelBrl,
            @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal customerFreightPerParcelBrl,
            @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal taxesPerUnitBrl,
            @Min(1) @Max(100) int maximumVerifiedUnits,@NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal maximumVerifiedSupplierBaseBrl,
            @Min(1) @Max(100) int unitsPerParcel,@NotNull Grouping grouping,boolean packagingVerified,
            @Min(1) @Max(365) int estimatedDays,@NotNull @Valid Evidence evidence) {}
    /** Planning rows are never read by checkout; only explicitly approved, validated rates are materialized. */
    public record ReviewRow(@NotNull Rate rate, boolean approved) {}
    /** Merchant risk acceptance, not supplier evidence. One eligible SMALL unit per order. */
    public record ProvisionalPolicy(@NotEmpty @Size(max=500) List<@NotNull Long> eligibleVariantIds,
            boolean singleSmallUnitParcelConfirmed,@NotNull Instant validUntil,
            @NotNull @DecimalMin("24.01") @Digits(integer=8,fraction=2) BigDecimal maximumOrderTotalBrl,
            @Min(1) @Max(10000) int maximumOpenOrders,
            @NotNull @DecimalMin("24.01") @Digits(integer=10,fraction=2) BigDecimal maximumOpenSalesBrl) {
        public ProvisionalPolicy {eligibleVariantIds=eligibleVariantIds==null?null:List.copyOf(eligibleVariantIds);}
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public BigDecimal customerFreightBrl(){return new BigDecimal("24.00");}
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public List<NationalCell> nationalRates(){
            return new TreeSet<>(com.garage.garageapi.integration.aliexpress.AliExpressDsFreightClient.BRAZILIAN_PROVINCES.keySet()).stream()
                .flatMap(uf->Arrays.stream(ParcelSize.values()).map(size->new NationalCell(uf,size,size==ParcelSize.SMALL?customerFreightBrl():null,size==ParcelSize.SMALL?"PROVISIONAL_ELIGIBLE_ONLY":"DRAFT_INACTIVE"))).toList();
        }
    }
    public record NationalCell(String state,ParcelSize size,BigDecimal customerFreightBrl,String status){}
    public record Document(@NotNull @DecimalMin("0.01") @DecimalMax("99.00") BigDecimal costReservePercent,
            @NotNull @DecimalMin("0.00") @DecimalMax("99.00") BigDecimal paymentFeePercent,
            @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal paymentFixedFeeBrl,
            @NotNull @DecimalMin("0.01") @DecimalMax("99.00") BigDecimal minimumNetMarginPercent,
            @NotNull @DecimalMin("0.00") BigDecimal minimumNetProfitBrl,@NotNull @Valid Evidence paymentFeeEvidence,
            @NotEmpty @Size(max=500) List<@NotNull @Valid Profile> profiles,@NotEmpty @Size(max=2000) List<@NotNull @Valid Rate> rates,
            List<ReviewRow> reviewRows,ProvisionalPolicy provisional) {
        public Document {profiles=profiles==null?null:List.copyOf(profiles);rates=rates==null?null:List.copyOf(rates);reviewRows=reviewRows==null?List.of():List.copyOf(reviewRows);}
        public Document(BigDecimal reserve,BigDecimal fee,BigDecimal fixed,BigDecimal margin,BigDecimal profit,Evidence evidence,List<Profile> profiles,List<Rate> rates){this(reserve,fee,fixed,margin,profit,evidence,profiles,rates,List.of(),null);}
        public Document(BigDecimal reserve,BigDecimal fee,BigDecimal fixed,BigDecimal margin,BigDecimal profit,Evidence evidence,List<Profile> profiles,List<Rate> rates,List<ReviewRow> rows){this(reserve,fee,fixed,margin,profit,evidence,profiles,rates,rows,null);}
    }
    public static BigDecimal money(BigDecimal n){return n.setScale(2,RoundingMode.HALF_UP);}
    public static void require(boolean ok,String message){if(!ok)throw new ResourceConflictException(message+" Solicite cotação assistida.");}
}
