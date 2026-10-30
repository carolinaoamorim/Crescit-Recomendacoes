package com.acessorinvestimentos.recomendacoes.dto;

import com.acessorinvestimentos.recomendacoes.entity.AssetType;
import com.acessorinvestimentos.recomendacoes.entity.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetDto {
    private String ticker;
    private String name;
    private AssetType assetType;
    private RiskLevel riskLevel;
    private BigDecimal currentPrice;
    private String sector;
    private Boolean active;
    private String rateType;
    private BigDecimal rateValue;
    private String liquidity;
    private Boolean taxExempt;
    private BigDecimal minimumInvestment;
    private String description;

    /** Formato original, sem os campos opcionais de renda fixa e descrição. */
    public AssetDto(String ticker, String name, AssetType assetType, RiskLevel riskLevel,
                    BigDecimal currentPrice, String sector, Boolean active) {
        this(ticker, name, assetType, riskLevel, currentPrice, sector, active, null, null, null, null, null, null);
    }
}
