package com.acessorinvestimentos.recomendacoes.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acessorinvestimentos.recomendacoes.entity.Asset;
import com.acessorinvestimentos.recomendacoes.entity.AssetType;
import com.acessorinvestimentos.recomendacoes.entity.RiskLevel;
import com.acessorinvestimentos.recomendacoes.repository.AssetRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogSeederTest {

    @Mock AssetRepository assetRepository;
    @InjectMocks CatalogSeeder seeder;

    @Test
    void shouldInsertOnlyMissingTickers() {
        Asset existing = new Asset();
        existing.setTicker("PETR4");
        existing.setCurrentPrice(new BigDecimal("40.00"));
        existing.setRiskLevel(RiskLevel.MEDIUM);
        existing.setDescription("Descrição do admin");
        existing.setLiquidity("D+2");
        when(assetRepository.findByTickerIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(assetRepository.findByTickerIgnoreCase("PETR4")).thenReturn(Optional.of(existing));

        seeder.run(null);

        ArgumentCaptor<Asset> saved = ArgumentCaptor.forClass(Asset.class);
        int total = CatalogSeeder.ReferenceCatalog.entries().size();
        verify(assetRepository, times(total - 1)).save(saved.capture());
        assertFalse(saved.getAllValues().stream().anyMatch(a -> a.getTicker().equals("PETR4")));
        // Ativo existente não é alterado: preço e risco continuam os do banco
        assertEquals(new BigDecimal("40.00"), existing.getCurrentPrice());
        assertEquals(RiskLevel.MEDIUM, existing.getRiskLevel());
    }

    @Test
    void shouldOnlyFillEmptyDescriptiveFields() {
        Asset target = new Asset();
        target.setCurrentPrice(BigDecimal.ONE);
        Asset reference = new Asset();
        reference.setDescription("Texto");
        reference.setLiquidity("Diária");
        reference.setCurrentPrice(BigDecimal.TEN);

        assertTrue(CatalogSeeder.fillMissingDetails(target, reference));
        assertEquals("Texto", target.getDescription());
        assertEquals(BigDecimal.ONE, target.getCurrentPrice());
        assertFalse(CatalogSeeder.fillMissingDetails(target, reference));
    }

    @Test
    void shouldHaveValidUniqueEntries() {
        Set<String> tickers = new HashSet<>();
        for (Asset a : CatalogSeeder.ReferenceCatalog.entries()) {
            assertTrue(tickers.add(a.getTicker()), "ticker repetido: " + a.getTicker());
            assertTrue(a.getCurrentPrice().signum() > 0);
            if (a.getRateType() != null) {
                assertEquals(AssetType.FIXED_INCOME, a.getAssetType());
                assertTrue(Set.of("CDI_PERCENT", "PREFIXED", "IPCA_PLUS").contains(a.getRateType()));
            }
        }
    }

    @Test
    void shouldNotSaveWhenEverythingExistsAndIsComplete() {
        when(assetRepository.findByTickerIgnoreCase(anyString())).thenAnswer(inv -> {
            Asset a = new Asset();
            a.setDescription("x");
            a.setLiquidity("y");
            return Optional.of(a);
        });
        seeder.run(null);
        verify(assetRepository, never()).save(any());
    }
}
