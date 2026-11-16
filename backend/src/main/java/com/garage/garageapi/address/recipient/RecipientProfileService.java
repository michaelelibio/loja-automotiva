package com.garage.garageapi.address.recipient;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.dto.AliExpressRecipientRequest;
import com.garage.garageapi.order.recipient.AliExpressRecipientData;
import com.garage.garageapi.shared.exception.*;
import com.garage.garageapi.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RecipientProfileService {
    private final AddressRepository addresses;
    private final RecipientProfileRepository profiles;
    private final UserService users;
    private final RecipientProfileCipher cipher;
    public RecipientProfileService(AddressRepository addresses,RecipientProfileRepository profiles,UserService users,RecipientProfileCipher cipher) {
        this.addresses=addresses;this.profiles=profiles;this.users=users;this.cipher=cipher;
    }
    public record View(boolean saved,String recipientName,AliExpressRecipientRequest recipient) {
        @Override public String toString() { return "RecipientProfileView[REDACTED]"; }
    }
    @Transactional(readOnly=true)
    public View get(Jwt jwt,Long id) { return view(owned(jwt,id,false)); }
    @Transactional(readOnly=true)
    public View adminGet(Jwt jwt,Long id) {
        if(users.findCurrentUser(jwt).getRole()!=com.garage.garageapi.user.entity.UserRole.ADMIN)
            throw new org.springframework.security.access.AccessDeniedException("Permissão administrativa necessária.");
        return view(addresses.findById(id).orElseThrow(()->new ResourceNotFoundException("Endereço não encontrado.")));
    }
    private View view(Address address) {
        var profile=profiles.findById(address.getId()).filter(p->p.getBinding().equals(binding(address))).orElse(null);
        if(profile==null)return new View(false,address.getRecipientName(),null);
        try {
            String[] parts=cipher.decrypt(profile.getCiphertext(),context(address)).split("\\|",-1);
            if(parts.length!=3)throw RecipientProfileCipher.unavailable();
            var recipient=new AliExpressRecipientRequest(parts[0],parts[1],parts[2]);
            AliExpressRecipientData.validate(recipient,address);
            return new View(true,address.getRecipientName(),recipient);
        } catch(RuntimeException ignored) { throw RecipientProfileCipher.unavailable(); }
    }
    @Transactional
    public void save(Jwt jwt,Long id,String expectedName,AliExpressRecipientRequest request) {
        var address=owned(jwt,id,true);
        if(!normalize(address.getRecipientName()).equals(normalize(expectedName)))
            throw new ResourceConflictException("O destinatário foi alterado. Atualize o endereço antes de salvar.");
        var data=AliExpressRecipientData.validate(request,address);
        String encrypted=cipher.encrypt(data.cpf()+"|"+data.birthDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))+"|"+data.phone(),context(address));
        var profile=profiles.findById(id).orElse(null);
        if(profile==null)profiles.save(new RecipientProfile(address,binding(address),encrypted));else profile.update(binding(address),encrypted);
    }
    @Transactional
    public void delete(Jwt jwt,Long id) { owned(jwt,id,true);profiles.deleteById(id); }
    private Address owned(Jwt jwt,Long id,boolean lock) {
        var user=users.findCurrentUser(jwt);
        return (lock?addresses.findOwnedForUpdate(id,user.getId()):addresses.findByIdAndUserId(id,user.getId()))
                .orElseThrow(()->new ResourceNotFoundException("Endereço não encontrado."));
    }
    private static String context(Address a) { return a.getUser().getId()+":"+a.getId()+":"+binding(a); }
    private static String normalize(String value) { return value==null?"":value.strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT); }
    public static String binding(Address a) {
        String value=String.join("\u0000",List.of(a.getRecipientName(),a.getZipCode(),a.getStreet(),a.getNumber(),
                Objects.toString(a.getComplement(),""),a.getNeighborhood(),a.getCity(),a.getState()).stream().map(RecipientProfileService::normalize).toList());
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("Digest unavailable");}
    }
}
