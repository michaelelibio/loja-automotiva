package com.garage.garageapi.integration.aliexpress;

import jakarta.persistence.*;
import java.time.Instant;

/** Audit deliberately excludes CPF, birth date, address, provider payload and free text. */
@Entity @Table(name="aliexpress_recipient_admin_audits")
public class AliExpressRecipientAdminAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="order_id",nullable=false) private Long orderId;
    @Column(name="actor_id",nullable=false,length=120) private String actorId;
    @Column(nullable=false,length=40) private String action;
    @Column(name="previous_context",nullable=false,length=64) private String previousContext;
    @Column(name="current_context",nullable=false,length=64) private String currentContext;
    @Column(name="attempt_count",nullable=false) private int attemptCount;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected AliExpressRecipientAdminAudit() { }
    public AliExpressRecipientAdminAudit(Long orderId,String actor,String action,String previous,String current,int attempts) {
        this.orderId=orderId;this.actorId=actor;this.action=action;previousContext=previous;currentContext=current;attemptCount=attempts;createdAt=Instant.now();
    }
    public Long getId() {return id;}
    public String getCurrentContext() {return currentContext;}
    public Instant getCreatedAt() {return createdAt;}
    @Override public String toString() {return "AliExpressRecipientAdminAudit[metadata only]";}
}
