package com.garage.garageapi.address.recipient;

import com.garage.garageapi.address.entity.Address;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="address_recipient_profiles")
public class RecipientProfile {
    @Id @Column(name="address_id") private Long addressId;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @MapsId @JoinColumn(name="address_id")
    @org.hibernate.annotations.OnDelete(action=org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Address address;
    @Column(nullable=false,length=64) private String binding;
    @Column(nullable=false,length=2048) private String ciphertext;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected RecipientProfile() { }
    RecipientProfile(Address address,String binding,String ciphertext) { this.address=address;update(binding,ciphertext); }
    void update(String binding,String ciphertext) { this.binding=binding;this.ciphertext=ciphertext;this.updatedAt=Instant.now();if(createdAt==null)createdAt=updatedAt; }
    public String getBinding() { return binding; }
    public String getCiphertext() { return ciphertext; }
    @Override public String toString() { return "RecipientProfile[REDACTED]"; }
}
