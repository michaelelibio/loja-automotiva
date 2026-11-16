package com.garage.garageapi.address;

import com.garage.garageapi.address.dto.AddressRequest;
import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.address.recipient.*;
import com.garage.garageapi.address.service.AddressService;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.order.dto.AliExpressRecipientRequest;
import com.garage.garageapi.order.recipient.AliExpressRecipientData;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.system.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.recipient.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class RecipientProfileIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AddressRepository addresses;
    @Autowired UserRepository users;
    @Autowired RecipientProfileRepository profiles;
    @Autowired RecipientProfileService service;
    @Autowired AddressService addressService;
    @Autowired OrderRepository orders;
    User owner,other;Address address;
    @BeforeEach void setup() {
        profiles.deleteAll();orders.deleteAll();addresses.deleteAll();users.deleteAll();
        owner=users.save(User.local("Cliente Teste","recipient-owner@example.test","synthetic"));
        other=users.save(User.local("Outra Pessoa","recipient-other@example.test","synthetic"));
        address=addresses.saveAndFlush(new Address(owner,"Casa","Cliente Teste","89229040","Rua Teste","10",null,"Centro","Joinville","SC",true));
    }
    @AfterEach void cleanup() {profiles.deleteAll();orders.deleteAll();addresses.deleteAll();users.deleteAll();}
    private String path() {return "/api/addresses/"+address.getId()+"/recipient";}
    private AliExpressRecipientRequest data() {return new AliExpressRecipientRequest("52998224725","29/02/2000","47999999999");}
    private Jwt token(User user) {return Jwt.withTokenValue("synthetic").header("alg","none").subject(user.getId().toString()).build();}
    private String body(AliExpressRecipientRequest data) {return JsonMapper.builder().build().writeValueAsString(Map.of("recipientName",address.getRecipientName(),"recipient",data));}
    private void save() throws Exception {
        mvc.perform(put(path()).with(jwt().jwt(j->j.subject(owner.getId().toString()))).contentType("application/json").content(body(data())))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","private, no-store"));
    }
    @Test void savesEncryptedDataAndPrefillsOnlyTheOwnedAddressWithoutLoggingPersonalValues(CapturedOutput output) throws Exception {
        mvc.perform(get(path()).with(jwt().jwt(j->j.subject(owner.getId().toString())))).andExpect(jsonPath("$.saved").value(false));
        save();
        var stored=profiles.findById(address.getId()).orElseThrow();
        assertThat(stored.getCiphertext()).startsWith("v1:").doesNotContain(data().cpf(),data().birthDate(),data().phone());
        mvc.perform(get(path()).with(jwt().jwt(j->j.subject(owner.getId().toString())))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.recipient.cpf").value(data().cpf())).andExpect(jsonPath("$.recipient.birthDate").value(data().birthDate()));
        assertThat(output.getAll()).doesNotContain(data().cpf(),data().birthDate(),data().phone());
    }
    @Test void authenticationOwnershipAndAdminAuthorizationAreEnforced() throws Exception {
        save();
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        for(var builder:java.util.List.of(get(path()),delete(path()),put(path()).contentType("application/json").content(body(data()))))
            mvc.perform(builder.with(jwt().jwt(j->j.subject(other.getId().toString())))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/addresses/"+address.getId()+"/recipient").with(jwt().jwt(j->j.subject(owner.getId().toString()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/addresses/"+address.getId()+"/recipient").with(jwt().jwt(j->j.subject(owner.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        var admin=User.local("Admin Teste","recipient-admin@example.test","synthetic");
        org.springframework.test.util.ReflectionTestUtils.setField(admin,"role",com.garage.garageapi.user.entity.UserRole.ADMIN);
        users.saveAndFlush(admin);
        mvc.perform(get("/api/admin/addresses/"+address.getId()+"/recipient").with(jwt().jwt(j->j.subject(admin.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));
        org.springframework.test.util.ReflectionTestUtils.setField(admin,"active",false);users.saveAndFlush(admin);
        mvc.perform(get("/api/admin/addresses/"+address.getId()+"/recipient").with(jwt().jwt(j->j.subject(admin.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        assertThat(profiles.count()).isEqualTo(1);
    }
    @Test void validatesCpfBirthDatePhoneAndCurrentRecipientBeforeSaving() throws Exception {
        for(var invalid:java.util.List.of(new AliExpressRecipientRequest("11111111111",data().birthDate(),data().phone()),
                new AliExpressRecipientRequest(data().cpf(),"31/02/2000",data().phone()),
                new AliExpressRecipientRequest(data().cpf(),data().birthDate(),"00000000000"))) {
            mvc.perform(put(path()).with(jwt().jwt(j->j.subject(owner.getId().toString()))).contentType("application/json").content(body(invalid)))
                    .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(invalid.toString()))));
        }
        mvc.perform(put(path()).with(jwt().jwt(j->j.subject(owner.getId().toString()))).contentType("application/json").content(body(data()).replace("Cliente Teste","Outra Pessoa")))
                .andExpect(status().isConflict());
        assertThat(profiles.count()).isZero();
    }
    @Test void editingProfilesNeverChangesHistoricalOrderSnapshotsAndChangingAddressInvalidatesPrefill() throws Exception {
        save();
        var order=new Order(owner,address,BigDecimal.TEN,BigDecimal.ZERO,Duration.ofHours(24));
        order.snapshotRecipient(AliExpressRecipientData.validate(data(),address));orders.saveAndFlush(order);
        service.save(token(owner),address.getId(),address.getRecipientName(),new AliExpressRecipientRequest(data().cpf(),"01/01/2001",data().phone()));
        assertThat(orders.findById(order.getId()).orElseThrow().getRecipientBirthDate()).isEqualTo(java.time.LocalDate.of(2000,2,29));
        addressService.update(token(owner),address.getId(),new AddressRequest("Casa","Outra Pessoa",address.getZipCode(),address.getStreet(),address.getNumber(),null,address.getNeighborhood(),address.getCity(),address.getState(),true));
        assertThat(service.get(token(owner),address.getId()).saved()).isFalse();assertThat(profiles.count()).isZero();
        assertThat(orders.findById(order.getId()).orElseThrow().getRecipientName()).isEqualTo("Cliente Teste");
    }
    @Test void userCanForgetSavedDataWithoutRemovingAddressOrHistoricalOrders() throws Exception {
        save();
        mvc.perform(delete(path()).with(jwt().jwt(j->j.subject(owner.getId().toString())))).andExpect(status().isNoContent());
        assertThat(profiles.count()).isZero();assertThat(addresses.existsById(address.getId())).isTrue();
    }
}
