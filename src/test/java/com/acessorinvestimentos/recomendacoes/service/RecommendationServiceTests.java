package com.acessorinvestimentos.recomendacoes.service;

import com.acessorinvestimentos.recomendacoes.client.InvestorProfileClient;
import com.acessorinvestimentos.recomendacoes.dto.AssetDto;
import com.acessorinvestimentos.recomendacoes.dto.ProfileResponseDto;
import com.acessorinvestimentos.recomendacoes.dto.RecommendationResponseDto;
import com.acessorinvestimentos.recomendacoes.entity.Asset;
import com.acessorinvestimentos.recomendacoes.entity.AssetType;
import com.acessorinvestimentos.recomendacoes.entity.InvestorType;
import com.acessorinvestimentos.recomendacoes.entity.Recommendation;
import com.acessorinvestimentos.recomendacoes.entity.RiskLevel;
import com.acessorinvestimentos.recomendacoes.exception.RecomendacaoException;
import com.acessorinvestimentos.recomendacoes.repository.AssetRepository;
import com.acessorinvestimentos.recomendacoes.repository.RecommendationRepository;
import com.acessorinvestimentos.recomendacoes.strategy.ModerateRecommendationStrategy;
import com.acessorinvestimentos.recomendacoes.strategy.ConservativeRecommendationStrategy;
import com.acessorinvestimentos.recomendacoes.strategy.RecommendationStrategyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class RecommendationServiceTests {

    @InjectMocks
    private RecommendationService recommendationService;

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private InvestorProfileClient investorProfileClient;

    @Mock
    private RecommendationStrategyFactory strategyFactory;

    @Test
    public void test_shouldGenerateRecommendationsForModerateInvestor() {
        String userId = "auth0|123";
        List<Asset> assets = List.of(
                createAsset("TESOURO", RiskLevel.LOW),
                createAsset("BOVA11", RiskLevel.MEDIUM),
                createAsset("ALTO3", RiskLevel.HIGH)
        );

        Mockito.when(investorProfileClient.getInvestorProfile(userId))
                .thenReturn(new ProfileResponseDto(userId, InvestorType.MODERATE));
        Mockito.when(assetRepository.findByActiveTrue()).thenReturn(assets);
        Mockito.when(strategyFactory.getStrategy(InvestorType.MODERATE))
                .thenReturn(new ModerateRecommendationStrategy());
        Mockito.when(recommendationRepository.saveAll(Mockito.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<RecommendationResponseDto> response = recommendationService.generateRecommendations(userId);

        Assertions.assertEquals(2, response.size());
        Assertions.assertTrue(response.stream().allMatch(dto -> dto.getInvestorType() == InvestorType.MODERATE));
        Assertions.assertTrue(response.stream().noneMatch(dto -> dto.getRiskLevel() == RiskLevel.HIGH));
    }

    @Test
    public void test_shouldReturnRecommendationsByUserId() {
        String userId = "auth0|456";
        Asset asset = createAsset("CDB001", RiskLevel.LOW);
        Recommendation recommendation = Recommendation.fromAsset(
                userId,
                InvestorType.CONSERVATIVE,
                asset,
                95,
                "Ativo de baixo risco"
        );

        Mockito.when(recommendationRepository.findByUserId(userId))
                .thenReturn(List.of(recommendation));
        Mockito.when(assetRepository.findAllById(Mockito.any()))
                .thenReturn(List.of(asset));

        List<RecommendationResponseDto> response = recommendationService.getRecommendationsByUserId(userId);

        Assertions.assertEquals(1, response.size());
        Assertions.assertEquals("CDB001", response.get(0).getTicker());
        Assertions.assertEquals(InvestorType.CONSERVATIVE, response.get(0).getInvestorType());
    }

    @Test
    public void test_shouldGenerateFromProfileEventWithoutCallingProtectedProfileApi() {
        String userId = "auth0|event-user";
        List<Asset> assets = List.of(createAsset("CDB001", RiskLevel.LOW));

        Mockito.when(assetRepository.findByActiveTrue()).thenReturn(assets);
        Mockito.when(strategyFactory.getStrategy(InvestorType.CONSERVATIVE))
                .thenReturn(new ConservativeRecommendationStrategy());
        Mockito.when(recommendationRepository.saveAll(Mockito.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<RecommendationResponseDto> response =
                recommendationService.generateRecommendations(userId, InvestorType.CONSERVATIVE);

        Assertions.assertEquals(1, response.size());
        Mockito.verifyNoInteractions(investorProfileClient);
    }

    @Test
    public void test_shouldDeleteOldRecommendations() {
        recommendationService.deleteOldRecommendations("auth0|789");

        Mockito.verify(recommendationRepository).deleteByUserId("auth0|789");
    }

    @Test
    public void test_shouldRejectInvalidUserId() {
        assertThrows(RecomendacaoException.class, () -> recommendationService.generateRecommendations(null));
        assertThrows(RecomendacaoException.class,
                () -> recommendationService.generateRecommendations("auth0|123", null));
        assertThrows(RecomendacaoException.class, () -> recommendationService.getRecommendationsByUserId(" "));
        assertThrows(RecomendacaoException.class, () -> recommendationService.deleteOldRecommendations(""));
        Mockito.verifyNoInteractions(recommendationRepository, assetRepository, investorProfileClient, strategyFactory);
    }

    private Asset createAsset(String ticker, RiskLevel riskLevel) {
        Asset asset = Asset.fromDto(new AssetDto(
                ticker,
                "Ativo " + ticker,
                AssetType.STOCK,
                riskLevel,
                new BigDecimal("100.00"),
                "Financeiro",
                true
        ));
        asset.setId(java.util.UUID.randomUUID());
        return asset;
    }
}
