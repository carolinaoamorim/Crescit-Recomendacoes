package com.acessorinvestimentos.recomendacoes.config;

import com.acessorinvestimentos.recomendacoes.controller.AssetController;
import com.acessorinvestimentos.recomendacoes.controller.HealthController;
import com.acessorinvestimentos.recomendacoes.controller.HelloController;
import com.acessorinvestimentos.recomendacoes.controller.RecommendationController;
import com.acessorinvestimentos.recomendacoes.dto.AssetDto;
import com.acessorinvestimentos.recomendacoes.service.AssetService;
import com.acessorinvestimentos.recomendacoes.service.RecommendationService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Exercise the real decoder with signed tokens and a local JWKS endpoint.
// These tests require neither Auth0 credentials, PostgreSQL nor Docker.
@WebMvcTest({AssetController.class, RecommendationController.class, HealthController.class, HelloController.class})
@Import(SecurityConfig.class)
class SecurityIntegrationTest {
    private static final String AUDIENCE = "https://api.crescit-investimentos";
    private static final String USER = "auth0|user-123";
    private static final String BODY = """
            {"ticker":"TEST3","name":"Ativo teste","assetType":"STOCK",
             "riskLevel":"LOW","currentPrice":10,"sector":"Teste","active":true}
            """;
    private static final RSAKey KEY = key();
    private static final HttpServer KEYS = startKeyServer();

    @Autowired MockMvc mvc;
    @MockitoBean AssetService assets;
    @MockitoBean RecommendationService recommendations;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", SecurityIntegrationTest::issuer);
        registry.add("auth0.audience", () -> AUDIENCE);
        registry.add("app.frontend-url", () -> "http://localhost:5173");
    }

    @AfterAll
    static void stopKeys() { KEYS.stop(0); }

    @ParameterizedTest
    @CsvSource({
            "GET,/assets", "GET,/assets/TEST3", "POST,/assets", "PUT,/assets/TEST3",
            "GET,/recommendations/auth0|user-123", "POST,/recommendations/auth0|user-123/generate", "GET,/hello"
    })
    void rejectsMissingToken(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(assets, recommendations);
    }

    @Test
    void rejectsMalformedToken() throws Exception {
        mvc.perform(get("/assets").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        assertUnauthorized(token(List.of("read:assets"), List.of(), claims -> claims
                .issueTime(Date.from(Instant.now().minusSeconds(900)))
                .expirationTime(Date.from(Instant.now().minusSeconds(300)))));
    }

    @Test
    void rejectsAnotherIssuer() throws Exception {
        assertUnauthorized(token(List.of("read:assets"), List.of(), claims -> claims.issuer("https://other.example/")));
    }

    @Test
    void rejectsAnotherAudience() throws Exception {
        assertUnauthorized(token(List.of("read:assets"), List.of(), claims -> claims.audience("https://another-api")));
    }

    @Test
    void rejectsMissingAudience() throws Exception {
        assertUnauthorized(token(List.of("read:assets"), List.of(), claims -> claims.audience((List<String>) null)));
    }

    @Test
    void rejectsMissingSubject() throws Exception {
        assertUnauthorized(token(List.of("read:assets"), List.of(), claims -> claims.subject(null)));
    }

    @Test
    void rejectsBlankSubject() throws Exception {
        assertUnauthorized(token(List.of("read:assets"), List.of(), claims -> claims.subject(" ")));
    }

    @Test
    void rejectsMissingExpiry() throws Exception {
        assertUnauthorized(token(List.of("read:assets"), List.of(), claims -> claims.expirationTime(null)));
    }

    @Test
    void rejectsNotYetValidToken() throws Exception {
        assertUnauthorized(token(List.of("read:assets"), List.of(),
                claims -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(200)))));
    }

    @Test
    void rejectsForgedSignature() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(token(List.of("read:assets"), List.of())).getJWTClaimsSet();
        assertUnauthorized(sign(claims, key(), JWSAlgorithm.RS256));
    }

    @Test
    void rejectsAnotherSigningAlgorithm() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(token(List.of("read:assets"), List.of())).getJWTClaimsSet();
        assertUnauthorized(sign(claims, KEY, JWSAlgorithm.RS512));
    }

    @Test
    void acceptsAudienceAmongSeveralAudiences() throws Exception {
        mvc.perform(get("/assets").header(HttpHeaders.AUTHORIZATION, bearer(
                        token(List.of("read:assets"), List.of(), claims -> claims.audience(List.of(AUDIENCE, issuer() + "userinfo"))))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingPermissions() throws Exception {
        mvc.perform(get("/assets").header(HttpHeaders.AUTHORIZATION, bearer(
                        token(List.of(), List.of(), claims -> claims.claim("permissions", null)))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assets);
    }

    @Test
    void doesNotUseScopesAsAnRbacPermission() throws Exception {
        mvc.perform(get("/assets").header(HttpHeaders.AUTHORIZATION, bearer(
                        token(List.of(), List.of(), claims -> claims.claim("scope", "read:assets")))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"GET,/assets", "GET,/assets/TEST3"})
    void allowsAssetReadingWithPermission(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header(HttpHeaders.AUTHORIZATION,
                        bearer(token(List.of("read:assets"), List.of("USER")))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"POST,/assets", "PUT,/assets/TEST3"})
    void forbidsAssetWritingWithoutWritePermission(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(List.of("read:assets"), List.of("USER"))))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assets);
    }

    @Test
    void adminStillNeedsWritePermission() throws Exception {
        mvc.perform(post("/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(List.of("read:assets"), List.of("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assets);
    }

    @Test
    void allowsAssetCreationWithWritePermissionAndNoRolesClaim() throws Exception {
        mvc.perform(post("/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(List.of("write:assets"), List.of())))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        verify(assets).createAsset(any(AssetDto.class));
    }

    @Test
    void allowsAssetUpdateWithWritePermissionAndNoRolesClaim() throws Exception {
        mvc.perform(put("/assets/TEST3")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(List.of("write:assets"), List.of())))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(assets).updateAsset(eq("TEST3"), any(AssetDto.class));
    }

    @Test
    void allowsOwnRecommendationsWithReadPermission() throws Exception {
        mvc.perform(get("/recommendations/{userId}", USER).header(HttpHeaders.AUTHORIZATION,
                        bearer(token(List.of("read:recommendations"), List.of("USER")))))
                .andExpect(status().isOk());
        verify(recommendations).getRecommendationsByUserId(USER);
    }

    @Test
    void allowsGenerationWithWritePermission() throws Exception {
        mvc.perform(post("/recommendations/{userId}/generate", USER).header(HttpHeaders.AUTHORIZATION,
                        bearer(token(List.of("write:recommendations"), List.of("USER")))))
                .andExpect(status().isOk());
        verify(recommendations).generateRecommendations(USER);
    }

    @ParameterizedTest
    @CsvSource({"GET,/recommendations/someone-else", "POST,/recommendations/someone-else/generate"})
    void evenAdminCannotUseAnotherUserId(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header(HttpHeaders.AUTHORIZATION,
                        bearer(token(List.of("read:recommendations", "write:recommendations"), List.of("ADMIN")))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(recommendations);
    }

    @ParameterizedTest
    @CsvSource({"GET,/recommendations/auth0|user-123,write:recommendations",
            "POST,/recommendations/auth0|user-123/generate,read:recommendations"})
    void readAndWritePermissionsAreIndependent(String method, String path, String permission) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header(HttpHeaders.AUTHORIZATION,
                        bearer(token(List.of(permission), List.of("USER")))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(recommendations);
    }

    @Test
    void allowsFrontendPreflightWithoutToken() throws Exception {
        mvc.perform(options("/assets").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        mvc.perform(options("/assets").header(HttpHeaders.ORIGIN, "http://other.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void rejectsUnsupportedCorsMethod() throws Exception {
        mvc.perform(options("/assets").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthDoesNotRequireAuthentication() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void unknownEndpointIsNotGrantedByToken() throws Exception {
        mvc.perform(get("/unmapped").header(HttpHeaders.AUTHORIZATION, bearer(token(List.of(), List.of()))))
                .andExpect(status().isForbidden());
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get("/assets").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(assets);
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private static String token(List<String> permissions, List<String> roles) throws Exception {
        return token(permissions, roles, claims -> {});
    }

    private static String token(List<String> permissions, List<String> roles,
            Consumer<JWTClaimsSet.Builder> customise) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(issuer()).audience(AUDIENCE)
                .subject(USER).issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("permissions", permissions);
        if (!roles.isEmpty()) claims.claim("roles", roles);
        customise.accept(claims);
        return sign(claims.build(), KEY, JWSAlgorithm.RS256);
    }

    private static String sign(JWTClaimsSet claims, RSAKey key, JWSAlgorithm algorithm) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey key() {
        try { return new RSAKeyGenerator(2048).keyID("test-key").generate(); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }

    private static String issuer() { return "http://127.0.0.1:" + KEYS.getAddress().getPort() + "/"; }

    private static HttpServer startKeyServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            return server;
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
}
