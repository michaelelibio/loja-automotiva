package com.garage.garageapi.address;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.auth.service.JwtService;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AddressIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AddressRepository addressRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @BeforeEach
    void cleanBefore() { cleanDatabase(); }

    @AfterEach
    void cleanAfter() { cleanDatabase(); }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/addresses").contentType(MediaType.APPLICATION_JSON)
                        .content(request(false)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/addresses/1/primary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchPrimaryCorsPreflightIsAllowed() throws Exception {
        mockMvc.perform(options("/api/addresses/1/primary")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("content-type")));
    }

    @Test
    void firstAddressBecomesPrimaryAndInputIsNormalized() throws Exception {
        User user = user("user@example.com");

        mockMvc.perform(post("/api/addresses").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"label":"  Casa  ","recipientName":"  Michael   Silva ",
                                 "zipCode":"89200-000","street":" Rua   Exemplo ","number":" 123 ",
                                 "complement":"   ","neighborhood":" Centro ","city":" Joinville ",
                                 "state":" sc ","isPrimary":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPrimary").value(true))
                .andExpect(jsonPath("$.label").value("Casa"))
                .andExpect(jsonPath("$.recipientName").value("Michael Silva"))
                .andExpect(jsonPath("$.zipCode").value("89200000"))
                .andExpect(jsonPath("$.street").value("Rua Exemplo"))
                .andExpect(jsonPath("$.number").value("123"))
                .andExpect(jsonPath("$.complement").isEmpty())
                .andExpect(jsonPath("$.state").value("SC"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void secondAddressCanExplicitlyBecomePrimaryAndListIsIsolated() throws Exception {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        Address first = saved(userA, "Primeiro", true);
        saved(userB, "Outro usuário", true);

        mockMvc.perform(post("/api/addresses").header("Authorization", bearer(userA))
                        .contentType(MediaType.APPLICATION_JSON).content(request(true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isPrimary").value(true));

        assertThat(addressRepository.findById(first.getId()).orElseThrow().isPrimary()).isFalse();
        mockMvc.perform(get("/api/addresses").header("Authorization", bearer(userA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.label == 'Outro usuário')]").isEmpty());
    }

    @Test
    void normalUpdatePreservesPrimaryAndPatchChangesIt() throws Exception {
        User user = user("user@example.com");
        Address first = saved(user, "Casa", true);
        Address second = saved(user, "Trabalho", false);

        mockMvc.perform(put("/api/addresses/{id}", first.getId()).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(request(false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isPrimary").value(true))
                .andExpect(jsonPath("$.street").value("Rua Exemplo"));

        mockMvc.perform(patch("/api/addresses/{id}/primary", second.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isPrimary").value(true));
        assertThat(addressRepository.findById(first.getId()).orElseThrow().isPrimary()).isFalse();
        assertThat(addressRepository.findById(second.getId()).orElseThrow().isPrimary()).isTrue();
    }

    @Test
    void deleteWorksAndDeletingPrimaryPromotesOldestRemaining() throws Exception {
        User user = user("user@example.com");
        Address primary = saved(user, "Casa", true);
        Address replacement = saved(user, "Trabalho", false);

        mockMvc.perform(delete("/api/addresses/{id}", primary.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());

        assertThat(addressRepository.findById(primary.getId())).isEmpty();
        assertThat(addressRepository.findById(replacement.getId()).orElseThrow().isPrimary()).isTrue();
    }

    @Test
    void anotherUsersAddressIsAlwaysReportedAsNotFound() throws Exception {
        User owner = user("owner@example.com");
        User attacker = user("attacker@example.com");
        Address address = saved(owner, "Casa", true);

        mockMvc.perform(put("/api/addresses/{id}", address.getId())
                        .header("Authorization", bearer(attacker)).contentType(MediaType.APPLICATION_JSON)
                        .content(request(false))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/addresses/{id}", address.getId())
                        .header("Authorization", bearer(attacker))).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/addresses/{id}/primary", address.getId())
                        .header("Authorization", bearer(attacker))).andExpect(status().isNotFound());

        assertThat(addressRepository.findById(address.getId())).isPresent();
    }

    @Test
    void invalidZipCodeStateAndRequiredFieldsReturnBadRequest() throws Exception {
        User user = user("user@example.com");
        assertInvalid(user, request(false).replace("89200-000", "123"));
        assertInvalid(user, request(false).replace("\"SC\"", "\"Santa Catarina\""));
        assertInvalid(user, request(false).replace("\"Michael\"", "\"   \""));
        assertInvalid(user, request(false).replace("\"Rua Exemplo\"", "\"   \""));
        assertInvalid(user, request(false).replace("\"123\"", "\"   \""));
        assertInvalid(user, request(false).replace("\"Centro\"", "\"   \""));
        assertInvalid(user, request(false).replace("\"Joinville\"", "\"   \""));
    }

    private void assertInvalid(User user, String body) throws Exception {
        mockMvc.perform(post("/api/addresses").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private void cleanDatabase() {
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User user(String email) {
        return userRepository.save(User.local("Usuário", email, passwordEncoder.encode("strongPass123")));
    }

    private Address saved(User user, String label, boolean primary) {
        return addressRepository.save(new Address(user, label, "Michael", "89200000", "Rua",
                "123", null, "Centro", "Joinville", "SC", primary));
    }

    private String bearer(User user) { return "Bearer " + jwtService.issue(user).value(); }

    private String request(boolean primary) {
        return """
                {"label":"Casa","recipientName":"Michael","zipCode":"89200-000",
                 "street":"Rua Exemplo","number":"123","complement":"Apto 10",
                 "neighborhood":"Centro","city":"Joinville","state":"SC","isPrimary":%s}
                """.formatted(primary);
    }
}
