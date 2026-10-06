package cl.duoc.bancoxyz.bff;

import cl.duoc.bancoxyz.bff.common.client.AccountServiceClient;
import cl.duoc.bancoxyz.bff.common.client.AccountServiceResponse;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BffWebSecurityTests {
    static final RSAKey SIGNING_KEY;
    static final HttpServer JWKS;
    static {
        try {
            SIGNING_KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            JWKS.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @AfterAll static void closeServer() { JWKS.stop(0); }
    @Autowired MockMvc mvc;
    @MockitoBean AccountServiceClient client;

    @Test void rejectsMissingTokenBeforeCallingBackend() throws Exception {
        mvc.perform(get("/api/web/cuentas/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(client);
    }
    @Test void rejectsMalformedToken() throws Exception {
        request("not-a-jwt", 401);
    }
    @Test void rejectsWrongSignature() throws Exception {
        request(token(new RSAKeyGenerator(2048).keyID("test-key").generate(),
                "http://localhost:9000", "bff-web", "web.read", Instant.now().plusSeconds(300)), 401);
    }
    @Test void rejectsExpiredToken() throws Exception {
        request(token(SIGNING_KEY, "http://localhost:9000", "bff-web", "web.read",
                Instant.now().minusSeconds(120)), 401);
    }
    @Test void rejectsWrongIssuer() throws Exception {
        request(token(SIGNING_KEY, "http://untrusted", "bff-web", "web.read",
                Instant.now().plusSeconds(300)), 401);
    }
    @Test void rejectsWrongAudience() throws Exception {
        request(token(SIGNING_KEY, "http://localhost:9000", "bff-mobile", "web.read",
                Instant.now().plusSeconds(300)), 401);
    }
    @Test void rejectsTokenWithoutReadScope() throws Exception {
        request(token(SIGNING_KEY, "http://localhost:9000", "bff-web", "web.info",
                Instant.now().plusSeconds(300)), 403);
        verifyNoInteractions(client);
    }
    @Test void acceptsSignedTokenWithReadScope() throws Exception {
        when(client.findAccount(1)).thenReturn(new AccountServiceResponse(1, "Demo", "AHORRO",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, List.of()));
        request(token(SIGNING_KEY, "http://localhost:9000", "bff-web", "web.read",
                Instant.now().plusSeconds(300)), 200);
        verify(client).findAccount(1);
    }
    @Test void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private void request(String token, int expected) throws Exception {
        mvc.perform(get("/api/web/cuentas/1").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected));
    }

    private String token(RSAKey key, String issuer, String audience, String scope, Instant expires) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
                new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("web-demo")
                    .issueTime(Date.from(Instant.now().minusSeconds(300)))
                    .expirationTime(Date.from(expires)).claim("scope", scope).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
