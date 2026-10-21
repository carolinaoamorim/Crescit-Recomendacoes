package com.acessorinvestimentos.recomendacoes.config;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RestTemplateConfigTest {
    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void forwardsCallerTokenToProfileService() {
        RestTemplate client = new RestTemplateConfig().restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        Jwt token = Jwt.withTokenValue("caller-access-token").header("alg", "RS256").subject("auth0|123").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token, List.of()));
        server.expect(requestTo("http://profile/profiles/user"))
                .andExpect(header("Authorization", "Bearer caller-access-token"))
                .andRespond(withSuccess());

        client.getForObject("http://profile/profiles/user", String.class);
        server.verify();
    }

    @Test
    void doesNotReuseATokenFromPreviousRequest() {
        RestTemplate client = new RestTemplateConfig().restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        Jwt token = Jwt.withTokenValue("previous-token").header("alg", "RS256").subject("auth0|123").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token, List.of()));
        server.expect(requestTo("http://profile/profiles/first-user"))
                .andExpect(header("Authorization", "Bearer previous-token"))
                .andRespond(withSuccess());
        server.expect(requestTo("http://profile/profiles/user"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess());

        client.getForObject("http://profile/profiles/first-user", String.class);
        SecurityContextHolder.clearContext();
        client.getForObject("http://profile/profiles/user", String.class);
        server.verify();
    }
}
