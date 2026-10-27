package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.sql.*;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightDiagnosticResult.Status.*;

/** Explicitly opt-in operational probe. No application boot/schema init or database writes. */
@EnabledIfEnvironmentVariable(named="ALIEXPRESS_TERRITORIAL_LIVE",matches="true")
class AliExpressTerritorialLiveProbeTests {
    @Test void queryUsingExistingPersistedOAuthAndWriteOnlySanitizedEvidence() throws Exception {
        AliExpressTerritorialDiagnosticResult result;
        String key=env("ALIEXPRESS_APP_KEY"),secret=env("ALIEXPRESS_APP_SECRET"),cipherKey=env("ALIEXPRESS_TOKEN_ENCRYPTION_KEY");
        if (cipherKey.isBlank()) {
            result=AliExpressTerritorialDiagnosticResult.failure(LOCAL_AUTHORIZATION_UNAVAILABLE,"LOCAL_ENCRYPTION_KEY_UNAVAILABLE");
        } else {
            try {
                String id=AliExpressOAuthStateService.hash(key);
                AliExpressAuthorizationRepository repository=mock(AliExpressAuthorizationRepository.class);
                Optional<AliExpressAuthorization> authorization=Optional.empty();
                // Read exactly the persisted access credential; never read a refresh token.
                try(Connection connection=DriverManager.getConnection(env("DB_URL"),env("DB_USERNAME"),env("DB_PASSWORD"))) {
                    connection.setAutoCommit(false);connection.setReadOnly(true);
                    try(var statement=connection.prepareStatement("select access_ciphertext, access_expires_at from aliexpress_authorizations where id = ?")) {
                        statement.setQueryTimeout(10);statement.setString(1,id);
                        try(var rows=statement.executeQuery()) {
                            if(rows.next()) {
                                var row=new AliExpressAuthorization(id);
                                var expiry=rows.getTimestamp(2);
                                row.update(rows.getString(1),null,expiry==null?null:expiry.toInstant(),null);
                                authorization=Optional.of(row);
                            }
                        }
                    } finally { connection.rollback(); }
                }
                when(repository.findById(id)).thenReturn(authorization);
                var tokens=new AliExpressTokenService(repository,new AliExpressTokenCipher(cipherKey),mock(AliExpressAuthService.class),key);
                result=new AliExpressTerritorialDiagnosticService(tokens,new AliExpressTerritorialClient(key,secret)).run();
                verify(repository).findById(id);verifyNoMoreInteractions(repository);
            } catch(SQLException ignored) {
                result=AliExpressTerritorialDiagnosticResult.failure(LOCAL_AUTHORIZATION_UNAVAILABLE,"LOCAL_STORAGE_UNAVAILABLE");
            }
        }
        Files.writeString(Path.of("target/territorial-live-sanitized.json"),JsonMapper.builder().build()
                .writeValueAsString(java.util.Map.of("credentialSource","PERSISTED_OAUTH","result",result)));
        System.out.println("Territorial probe: "+result.status()+" / "+result.safeCode());
    }
    private static String env(String name) { String value=System.getenv(name);return value==null?"":value; }
}
