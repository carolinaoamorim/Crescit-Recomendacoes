package com.acessorinvestimentos.recomendacoes.controller;

import com.acessorinvestimentos.recomendacoes.client.InvestorProfileClient;
import com.acessorinvestimentos.recomendacoes.dto.AssetDto;
import com.acessorinvestimentos.recomendacoes.dto.ProfileResponseDto;
import com.acessorinvestimentos.recomendacoes.entity.AssetType;
import com.acessorinvestimentos.recomendacoes.entity.InvestorType;
import com.acessorinvestimentos.recomendacoes.entity.RiskLevel;
import com.acessorinvestimentos.recomendacoes.repository.AssetRepository;
import com.acessorinvestimentos.recomendacoes.repository.RecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public class AssetControllerTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("recomendacoes_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private RecommendationRepository recommendationRepository;

    @MockBean
    private InvestorProfileClient investorProfileClient;

    @BeforeEach
    public void cleanDatabase() {
        recommendationRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @Test
    public void test_shouldCreateAsset() throws Exception {
        AssetDto dto = createAssetDto("PETR4", RiskLevel.MEDIUM);

        mockMvc.perform(post("/assets").with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ticker").value("PETR4"))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"));
    }

    @Test
    public void test_shouldGenerateRecommendations() throws Exception {
        createAsset("TESOURO", RiskLevel.LOW);
        createAsset("BOVA11", RiskLevel.MEDIUM);
        createAsset("ALTO3", RiskLevel.HIGH);

        Mockito.when(investorProfileClient.getInvestorProfile("auth0|123"))
                .thenReturn(new ProfileResponseDto("auth0|123", InvestorType.MODERATE));

        mockMvc.perform(post("/recommendations/{userId}/generate", "auth0|123").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].userId").value("auth0|123"));
    }

    @Test
    public void test_shouldListAssets() throws Exception {
        createAsset("ITUB4", RiskLevel.MEDIUM);
        createAsset("TESOURO", RiskLevel.LOW);

        mockMvc.perform(get("/assets").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(2)));
    }

    private void createAsset(String ticker, RiskLevel riskLevel) throws Exception {
        AssetDto dto = createAssetDto(ticker, riskLevel);
        mockMvc.perform(post("/assets").with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("auth0|123")).authorities(
                new SimpleGrantedAuthority("read:assets"),
                new SimpleGrantedAuthority("write:assets"),
                new SimpleGrantedAuthority("write:recommendations"));
    }

    private AssetDto createAssetDto(String ticker, RiskLevel riskLevel) {
        return new AssetDto(
                ticker,
                "Ativo " + ticker,
                AssetType.STOCK,
                riskLevel,
                new BigDecimal("100.00"),
                "Financeiro",
                true
        );
    }
}
