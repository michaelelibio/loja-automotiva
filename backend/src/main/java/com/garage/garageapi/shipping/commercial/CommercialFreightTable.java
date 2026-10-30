package com.garage.garageapi.shipping.commercial;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity @Table(name="commercial_freight_tables")
public class CommercialFreightTable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb")
    private CommercialFreightPolicy.Document document;
    @Column(nullable=false) private Instant createdAt;
    private Instant publishedAt;
    @Column(nullable=false) private boolean enabled;
    protected CommercialFreightTable() {}
    public CommercialFreightTable(CommercialFreightPolicy.Document document) {this.document=document;createdAt=Instant.now();}
    public Long getId(){return id;} public CommercialFreightPolicy.Document getDocument(){return document;}
    public Instant getCreatedAt(){return createdAt;} public Instant getPublishedAt(){return publishedAt;} public boolean isEnabled(){return enabled;}
    public void publish(){if(publishedAt!=null)throw new IllegalStateException("Versão já publicada");publishedAt=Instant.now();enabled=true;}
    public void retire(){enabled=false;}
}
