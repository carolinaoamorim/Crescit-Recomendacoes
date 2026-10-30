package com.acessorinvestimentos.recomendacoes.entity;

import com.acessorinvestimentos.recomendacoes.dto.AssetDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "assets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal currentPrice;

    @Column
    private String sector;

    @Column(nullable = false)
    private Boolean active;

    @Column
    private LocalDateTime lastPriceUpdate;

    // Campos opcionais, usados por produtos de renda fixa e para explicar o ativo
    // na tela de detalhe. Ficam nulos nos ativos de bolsa.

    /** CDI_PERCENT, PREFIXED ou IPCA_PLUS (texto, igual ao RateType do Portfólio) */
    @Column(length = 20)
    private String rateType;

    @Column(precision = 9, scale = 4)
    private BigDecimal rateValue;

    /** Ex.: "Diária", "No vencimento", "Após 90 dias" */
    @Column(length = 40)
    private String liquidity;

    /** LCI e LCA são isentas de IR para pessoa física */
    @Column
    private Boolean taxExempt;

    @Column(precision = 19, scale = 2)
    private BigDecimal minimumInvestment;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Asset fromDto(AssetDto dto) {
        Asset asset = new Asset();
        asset.setTicker(dto.getTicker().trim().toUpperCase(Locale.ROOT));
        asset.setName(dto.getName());
        asset.setAssetType(dto.getAssetType());
        asset.setRiskLevel(dto.getRiskLevel());
        asset.setCurrentPrice(dto.getCurrentPrice());
        asset.setSector(dto.getSector());
        asset.setActive(dto.getActive() == null || dto.getActive());
        asset.setRateType(dto.getRateType());
        asset.setRateValue(dto.getRateValue());
        asset.setLiquidity(dto.getLiquidity());
        asset.setTaxExempt(dto.getTaxExempt());
        asset.setMinimumInvestment(dto.getMinimumInvestment());
        asset.setDescription(dto.getDescription());
        return asset;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (lastPriceUpdate == null) {
            lastPriceUpdate = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
