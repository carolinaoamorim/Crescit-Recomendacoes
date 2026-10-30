package com.acessorinvestimentos.recomendacoes.service;

import com.acessorinvestimentos.recomendacoes.dto.AssetDto;
import com.acessorinvestimentos.recomendacoes.entity.Asset;
import com.acessorinvestimentos.recomendacoes.exception.RecomendacaoException;
import com.acessorinvestimentos.recomendacoes.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;

    @Transactional
    public Asset createAsset(AssetDto dto) {
        validateCreate(dto);

        String ticker = normalizeTicker(dto.getTicker());
        if (assetRepository.existsByTickerIgnoreCase(ticker)) {
            throw new RecomendacaoException("Ativo ja cadastrado para o ticker " + ticker);
        }

        Asset asset = Asset.fromDto(dto);
        return assetRepository.save(asset);
    }

    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    public Asset getAssetByTicker(String ticker) {
        return assetRepository.findByTickerIgnoreCase(normalizeTicker(ticker))
                .orElseThrow(() -> new RecomendacaoException("Ativo nao encontrado"));
    }

    @Transactional
    public Asset updateAsset(String ticker, AssetDto dto) {
        Asset asset = getAssetByTicker(ticker);

        if (dto.getTicker() != null && !dto.getTicker().isBlank()) {
            String newTicker = normalizeTicker(dto.getTicker());
            if (!newTicker.equals(asset.getTicker()) && assetRepository.existsByTickerIgnoreCase(newTicker)) {
                throw new RecomendacaoException("Ativo ja cadastrado para o ticker " + newTicker);
            }
            asset.setTicker(newTicker);
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            asset.setName(dto.getName());
        }
        if (dto.getAssetType() != null) {
            asset.setAssetType(dto.getAssetType());
        }
        if (dto.getRiskLevel() != null) {
            asset.setRiskLevel(dto.getRiskLevel());
        }
        if (dto.getCurrentPrice() != null) {
            validatePrice(dto.getCurrentPrice());
            asset.setCurrentPrice(dto.getCurrentPrice());
            asset.setLastPriceUpdate(LocalDateTime.now());
        }
        if (dto.getSector() != null) {
            asset.setSector(dto.getSector());
        }
        if (dto.getActive() != null) {
            asset.setActive(dto.getActive());
        }
        if (dto.getRateType() != null) {
            asset.setRateType(dto.getRateType());
        }
        if (dto.getRateValue() != null) {
            asset.setRateValue(dto.getRateValue());
        }
        if (dto.getLiquidity() != null) {
            asset.setLiquidity(dto.getLiquidity());
        }
        if (dto.getTaxExempt() != null) {
            asset.setTaxExempt(dto.getTaxExempt());
        }
        if (dto.getMinimumInvestment() != null) {
            asset.setMinimumInvestment(dto.getMinimumInvestment());
        }
        if (dto.getDescription() != null) {
            asset.setDescription(dto.getDescription());
        }

        return assetRepository.save(asset);
    }

    @Transactional
    public Asset updateAssetPrice(String ticker, BigDecimal currentPrice) {
        validatePrice(currentPrice);
        Asset asset = getAssetByTicker(ticker);
        asset.setCurrentPrice(currentPrice);
        asset.setLastPriceUpdate(LocalDateTime.now());
        return assetRepository.save(asset);
    }

    @Transactional
    public Asset deactivateAsset(String ticker) {
        Asset asset = getAssetByTicker(ticker);
        asset.setActive(false);
        return assetRepository.save(asset);
    }

    private void validateCreate(AssetDto dto) {
        if (dto == null) {
            throw new RecomendacaoException("Dados do ativo sao obrigatorios");
        }
        if (dto.getTicker() == null || dto.getTicker().isBlank()) {
            throw new RecomendacaoException("Ticker e obrigatorio");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new RecomendacaoException("Nome do ativo e obrigatorio");
        }
        if (dto.getAssetType() == null) {
            throw new RecomendacaoException("Tipo do ativo e obrigatorio");
        }
        if (dto.getRiskLevel() == null) {
            throw new RecomendacaoException("Nivel de risco e obrigatorio");
        }
        validatePrice(dto.getCurrentPrice());
    }

    private void validatePrice(BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new RecomendacaoException("Preco atual deve ser maior ou igual a zero");
        }
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new RecomendacaoException("Ticker e obrigatorio");
        }
        return ticker.trim().toUpperCase(Locale.ROOT);
    }
}
