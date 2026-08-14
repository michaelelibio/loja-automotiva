package com.garage.garageapi.vehicle;

import com.garage.garageapi.auth.service.JwtService;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.vehicle.entity.Vehicle;
import com.garage.garageapi.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Year;

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
class VehicleIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        vehicleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void firstVehicleBecomesPrimaryAndSecondDoesNot() throws Exception {
        User user = user("user@example.com");

        mockMvc.perform(post("/api/vehicles").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(vehicle("Ford", "Ka", 2020, false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isPrimary").value(true))
                .andExpect(jsonPath("$.licensePlate").value("ABC1D23"))
                .andExpect(jsonPath("$.imageUrl").isEmpty());
        mockMvc.perform(post("/api/vehicles").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(vehicle("Honda", "Civic", 2022, false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isPrimary").value(false));

        assertThat(vehicleRepository.findAllByUserIdAndPrimaryTrue(user.getId())).hasSize(1);
    }

    @Test
    void listIsLimitedToAuthenticatedUserAndPrimaryCanBeChanged() throws Exception {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        Vehicle first = saved(userA, "Ford", true);
        Vehicle second = saved(userA, "Honda", false);
        saved(userB, "Toyota", true);

        mockMvc.perform(get("/api/vehicles").header("Authorization", bearer(userA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.brand == 'Toyota')]").isEmpty());
        mockMvc.perform(patch("/api/vehicles/{id}/primary", second.getId())
                        .header("Authorization", bearer(userA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isPrimary").value(true));

        assertThat(vehicleRepository.findById(first.getId()).orElseThrow().isPrimary()).isFalse();
        assertThat(vehicleRepository.findById(second.getId()).orElseThrow().isPrimary()).isTrue();
    }

    @Test
    void vehicleCanBeEditedAndDeleted() throws Exception {
        User user = user("user@example.com");
        Vehicle vehicle = saved(user, "Ford", true);

        mockMvc.perform(put("/api/vehicles/{id}", vehicle.getId()).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brand":"  Chevrolet ","model":" Onix  Plus ","year":2024,
                                 "version":" Premier ","licensePlate":" abc1234 ","isPrimary":true}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.brand").value("Chevrolet"))
                .andExpect(jsonPath("$.model").value("Onix Plus"))
                .andExpect(jsonPath("$.licensePlate").value("ABC1234"));
        mockMvc.perform(delete("/api/vehicles/{id}", vehicle.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());
        assertThat(vehicleRepository.findById(vehicle.getId())).isEmpty();
    }

    @Test
    void imageUrlCanBeCreatedReturnedUpdatedAndRemoved() throws Exception {
        User user = user("image@example.com");
        String authorization = bearer(user);

        String response = mockMvc.perform(post("/api/vehicles").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brand":"Peugeot","model":"308","year":2013,
                                 "version":"Allure 2.0","licensePlate":"ABC1D23","isPrimary":true,
                                 "imageUrl":"  https://cdn.exemplo.com/vehicles/123.webp  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl")
                        .value("https://cdn.exemplo.com/vehicles/123.webp"))
                .andReturn().getResponse().getContentAsString();
        Long vehicleId = Long.valueOf(response.replaceAll(".*\\\"id\\\":([0-9]+).*", "$1"));

        mockMvc.perform(get("/api/vehicles").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imageUrl")
                        .value("https://cdn.exemplo.com/vehicles/123.webp"));

        mockMvc.perform(put("/api/vehicles/{id}", vehicleId).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brand":"Peugeot","model":"308","year":2013,
                                 "version":"Allure 2.0","licensePlate":"ABC1D23","isPrimary":true,
                                 "imageUrl":"https://cdn.exemplo.com/vehicles/updated.webp"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl")
                        .value("https://cdn.exemplo.com/vehicles/updated.webp"));

        mockMvc.perform(put("/api/vehicles/{id}", vehicleId).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brand":"Peugeot","model":"308","year":2013,
                                 "version":"Allure 2.0","licensePlate":"ABC1D23","isPrimary":true,
                                 "imageUrl":null}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").isEmpty());

        assertThat(vehicleRepository.findById(vehicleId).orElseThrow().getImageUrl()).isNull();
    }

    @Test
    void deletingPrimaryPromotesAnotherVehicle() throws Exception {
        User user = user("user@example.com");
        Vehicle primary = saved(user, "Ford", true);
        Vehicle replacement = saved(user, "Honda", false);

        mockMvc.perform(delete("/api/vehicles/{id}", primary.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());

        assertThat(vehicleRepository.findById(replacement.getId()).orElseThrow().isPrimary()).isTrue();
    }

    @Test
    void userCannotChangeOrDeleteAnotherUsersVehicle() throws Exception {
        User owner = user("owner@example.com");
        User attacker = user("attacker@example.com");
        Vehicle vehicle = saved(owner, "Ford", true);

        mockMvc.perform(put("/api/vehicles/{id}", vehicle.getId()).header("Authorization", bearer(attacker))
                        .contentType(MediaType.APPLICATION_JSON).content(vehicle("Honda", "Civic", 2020, true)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/vehicles/{id}", vehicle.getId())
                        .header("Authorization", bearer(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/vehicles/{id}/primary", vehicle.getId())
                        .header("Authorization", bearer(attacker)))
                .andExpect(status().isNotFound());

        assertThat(vehicleRepository.findById(vehicle.getId())).isPresent();
    }

    @Test
    void authenticationAndInputValidationAreEnforced() throws Exception {
        User user = user("user@example.com");
        mockMvc.perform(get("/api/vehicles")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(vehicle("Ford", "Ka", 2020, false)))
                .andExpect(status().isUnauthorized());

        assertInvalid(user, "{\"brand\":\"   \",\"model\":\"Ka\",\"year\":2020}");
        assertInvalid(user, "{\"brand\":\"Ford\",\"model\":\"   \",\"year\":2020}");
        assertInvalid(user, "{\"brand\":\"Ford\",\"model\":\"Ka\",\"year\":2020,\"licensePlate\":\"INVALIDA\"}");
        assertInvalid(user, "{\"brand\":\"Ford\",\"model\":\"Ka\",\"year\":1885}");
        assertInvalid(user, "{\"brand\":\"Ford\",\"model\":\"Ka\",\"year\":"
                + (Year.now().getValue() + 2) + "}");
    }

    @Test
    void patchPrimaryCorsPreflightIsAllowedAndRealRequestRequiresJwt() throws Exception {
        mockMvc.perform(options("/api/vehicles/1/primary")
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

        mockMvc.perform(patch("/api/vehicles/1/primary"))
                .andExpect(status().isUnauthorized());
    }

    private void assertInvalid(User user, String body) throws Exception {
        mockMvc.perform(post("/api/vehicles").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private User user(String email) {
        return userRepository.save(User.local("Usuário", email, passwordEncoder.encode("strongPass123")));
    }

    private Vehicle saved(User user, String brand, boolean primary) {
        return vehicleRepository.save(new Vehicle(user, brand, "Modelo", 2020, null, null, primary));
    }

    private String bearer(User user) { return "Bearer " + jwtService.issue(user).value(); }

    private String vehicle(String brand, String model, int year, boolean primary) {
        return "{\"brand\":\"" + brand + "\",\"model\":\"" + model + "\",\"year\":" + year
                + ",\"version\":null,\"licensePlate\":\" abc1d23 \",\"isPrimary\":" + primary + "}";
    }
}
