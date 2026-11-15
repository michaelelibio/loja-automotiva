package com.garage.garageapi.shipping.commercial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class PostalStateResolverTests {
    @Test void resolvesOnlyTheRequestedPostalCodeAndCachesStateWithoutAddress(){var b=RestClient.builder().baseUrl("https://viacep.com.br");var server=MockRestServiceServer.bindTo(b).build();server.expect(requestTo("https://viacep.com.br/ws/89229040/json/")).andRespond(withSuccess("{\"cep\":\"89229-040\",\"uf\":\"SC\"}",MediaType.APPLICATION_JSON));var resolver=new PostalStateResolver(b.build());assertThat(resolver.resolve("89229040")).isEqualTo("SC");assertThat(resolver.resolve("89229040")).isEqualTo("SC");server.verify();}
    @ParameterizedTest @ValueSource(strings={"{\"erro\":true}","{\"cep\":\"01001-000\",\"uf\":\"SC\"}","{\"cep\":\"89229-040\",\"uf\":\"XX\"}"})
    void missingInvalidOrMismatchedPostalCodeDoesNotGuessState(String body){var b=RestClient.builder().baseUrl("https://viacep.com.br");var server=MockRestServiceServer.bindTo(b).build();server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));assertThatThrownBy(()->new PostalStateResolver(b.build()).resolve("89229040")).isInstanceOf(com.garage.garageapi.shared.exception.ResourceConflictException.class);server.verify();}
}
