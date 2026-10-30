package com.acessorinvestimentos.recomendacoes.catalog;

import com.acessorinvestimentos.recomendacoes.entity.Asset;
import com.acessorinvestimentos.recomendacoes.entity.AssetType;
import com.acessorinvestimentos.recomendacoes.entity.RiskLevel;
import com.acessorinvestimentos.recomendacoes.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Garante o catálogo de referência em todos os ambientes. Só INSERE os tickers
 * que ainda não existem e só PREENCHE descrição e liquidez quando estão vazias:
 * preço, risco e demais dados de ativos já cadastrados nunca são alterados.
 * Desligue com app.catalog.seed.enabled=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.catalog.seed.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogSeeder implements ApplicationRunner {

    private final AssetRepository assetRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        int completed = 0;
        for (Asset entry : ReferenceCatalog.entries()) {
            var existing = assetRepository.findByTickerIgnoreCase(entry.getTicker());
            if (existing.isEmpty()) {
                assetRepository.save(entry);
                created++;
            } else if (fillMissingDetails(existing.get(), entry)) {
                assetRepository.save(existing.get());
                completed++;
            }
        }
        log.info("Catálogo de referência: {} ativos criados, {} completados", created, completed);
    }

    /** Preenche apenas campos descritivos vazios. Devolve true se algo mudou. */
    static boolean fillMissingDetails(Asset target, Asset reference) {
        boolean changed = false;
        if (target.getDescription() == null && reference.getDescription() != null) {
            target.setDescription(reference.getDescription());
            changed = true;
        }
        if (target.getLiquidity() == null && reference.getLiquidity() != null) {
            target.setLiquidity(reference.getLiquidity());
            changed = true;
        }
        return changed;
    }

    /** Catálogo de referência. Preços e taxas são ilustrativos, não cotações em tempo real. */
    static final class ReferenceCatalog {

        private ReferenceCatalog() {
        }

        static List<Asset> entries() {
            return List.of(
                    // Renda fixa: títulos do Tesouro (preço por título)
                    market("TESOURO-SELIC", "Tesouro Selic 2029", AssetType.FIXED_INCOME, RiskLevel.LOW, "15890.42", "Tesouro",
                            "Diária",
                            "Título público que acompanha a Selic. Oscila muito pouco e pode ser resgatado a qualquer dia: indicado para a reserva de emergência."),
                    market("TESOURO-IPCA", "Tesouro IPCA+ 2035", AssetType.FIXED_INCOME, RiskLevel.LOW, "2210.15", "Tesouro",
                            "Diária (com oscilação de preço)",
                            "Rende a inflação mais uma taxa fixa. Protege o poder de compra no longo prazo; se vender antes do vencimento, o valor pode oscilar."),
                    market("TESOURO-PREFIXADO", "Tesouro Prefixado 2029", AssetType.FIXED_INCOME, RiskLevel.LOW, "650.30", "Tesouro",
                            "Diária (com oscilação de preço)",
                            "Taxa definida na compra: você sabe quanto vai receber no vencimento. Bom quando se espera queda dos juros."),

                    // Renda fixa: produtos de referência (taxas típicas, não ofertas de um banco)
                    product("CDB-LIQUIDEZ-DIARIA", "CDB com liquidez diária", "CDI_PERCENT", "100", "Diária", false, "100",
                            "Rende o CDI e pode ser resgatado a qualquer dia. Coberto pelo FGC até R$ 250 mil por instituição. Alternativa ao Tesouro Selic para a reserva."),
                    product("CDB-PREFIXADO-2A", "CDB prefixado 2 anos", "PREFIXED", "13.5", "No vencimento", false, "500",
                            "Taxa fixa ao ano por 2 anos. Costuma pagar mais que o CDB de liquidez diária porque o dinheiro fica parado até o vencimento."),
                    product("CDB-IPCA-3A", "CDB IPCA+ 3 anos", "IPCA_PLUS", "7", "No vencimento", false, "1000",
                            "Inflação mais uma taxa fixa por 3 anos, com cobertura do FGC. Para objetivos de médio prazo que precisam acompanhar os preços."),
                    product("LCI-90D", "LCI com carência de 90 dias", "CDI_PERCENT", "92", "Após 90 dias", true, "1000",
                            "Isenta de IR para pessoa física e coberta pelo FGC. Mesmo com um percentual do CDI menor, pode render mais que um CDB depois do imposto."),
                    product("LCA-1A", "LCA 1 ano", "CDI_PERCENT", "94", "No vencimento", true, "1000",
                            "Ligada ao agronegócio, isenta de IR e coberta pelo FGC. Bom complemento para a parte de renda fixa da carteira."),

                    // ETFs
                    market("BOVA11", "iShares Ibovespa", AssetType.ETF, RiskLevel.MEDIUM, "131.20", "Índice", "D+2",
                            "Fundo de índice que replica o Ibovespa: com uma compra, você investe nas maiores empresas da bolsa."),
                    market("IVVB11", "iShares S&P 500", AssetType.ETF, RiskLevel.MEDIUM, "342.50", "Índice", "D+2",
                            "Replica o S&P 500, as 500 maiores empresas dos Estados Unidos, em reais. Diversifica para fora do Brasil."),
                    market("IMAB11", "It Now IMA-B", AssetType.ETF, RiskLevel.LOW, "84.10", "Renda fixa", "D+2",
                            "ETF de títulos públicos atrelados à inflação. Renda fixa negociada na bolsa, com oscilação moderada."),
                    market("DIVO11", "It Now IDIV", AssetType.ETF, RiskLevel.MEDIUM, "98.40", "Índice", "D+2",
                            "Reúne empresas que pagam bons dividendos. Para quem busca renda e valorização no longo prazo."),
                    market("SMAL11", "iShares Small Cap", AssetType.ETF, RiskLevel.HIGH, "94.70", "Índice", "D+2",
                            "Empresas menores da bolsa: mais potencial de crescimento e também mais oscilação."),

                    // Fundos imobiliários
                    market("HGLG11", "CSHG Logística", AssetType.FII, RiskLevel.MEDIUM, "156.80", "Imobiliário", "D+2",
                            "Galpões logísticos alugados para grandes empresas. Paga rendimentos mensais isentos de IR."),
                    market("KNRI11", "Kinea Renda Imobiliária", AssetType.FII, RiskLevel.MEDIUM, "139.90", "Imobiliário", "D+2",
                            "Escritórios e galpões com contratos longos. Um dos FIIs mais tradicionais, com renda mensal."),
                    market("MXRF11", "Maxi Renda", AssetType.FII, RiskLevel.MEDIUM, "9.55", "Imobiliário", "D+2",
                            "Fundo de recebíveis imobiliários com cota baixa: dá para começar com pouco e receber renda mensal."),
                    market("XPML11", "XP Malls", AssetType.FII, RiskLevel.MEDIUM, "104.60", "Imobiliário", "D+2",
                            "Participação em shopping centers. A renda acompanha o desempenho do varejo."),

                    // Ações
                    market("ITUB4", "Itaú Unibanco PN", AssetType.STOCK, RiskLevel.MEDIUM, "39.10", "Financeiro", "D+2",
                            "Maior banco privado do país, com histórico de lucros estáveis e pagamento de dividendos."),
                    market("BBAS3", "Banco do Brasil ON", AssetType.STOCK, RiskLevel.MEDIUM, "27.40", "Financeiro", "D+2",
                            "Banco com forte presença no agronegócio e dividendos elevados; tem controle estatal."),
                    market("WEGE3", "WEG ON", AssetType.STOCK, RiskLevel.MEDIUM, "41.20", "Industrial", "D+2",
                            "Fabricante de motores e equipamentos elétricos com crescimento consistente e atuação global."),
                    market("TAEE11", "Taesa Unit", AssetType.STOCK, RiskLevel.MEDIUM, "35.80", "Energia", "D+2",
                            "Transmissão de energia com receitas previsíveis: costuma ser escolhida por quem busca dividendos."),
                    market("PETR4", "Petrobras PN", AssetType.STOCK, RiskLevel.HIGH, "36.45", "Energia", "D+2",
                            "Petróleo e gás: dividendos altos, mas sensível ao preço do petróleo e a decisões do governo."),
                    market("VALE3", "Vale ON", AssetType.STOCK, RiskLevel.HIGH, "61.30", "Mineração", "D+2",
                            "Uma das maiores mineradoras do mundo; o resultado depende do preço do minério de ferro e da China."),
                    market("MGLU3", "Magazine Luiza ON", AssetType.STOCK, RiskLevel.HIGH, "8.72", "Varejo", "D+2",
                            "Varejo com forte presença digital. Ação muito volátil, sensível a juros e ao consumo.")
            );
        }

        private static Asset market(String ticker, String name, AssetType type, RiskLevel risk, String price, String sector,
                                    String liquidity, String description) {
            Asset asset = new Asset();
            asset.setTicker(ticker);
            asset.setName(name);
            asset.setAssetType(type);
            asset.setRiskLevel(risk);
            asset.setCurrentPrice(new BigDecimal(price));
            asset.setSector(sector);
            asset.setActive(true);
            asset.setLiquidity(liquidity);
            asset.setDescription(description);
            return asset;
        }

        /** Renda fixa por taxa: o "preço" é a aplicação mínima, para manter o campo obrigatório preenchido. */
        private static Asset product(String ticker, String name, String rateType, String rateValue, String liquidity,
                                     boolean taxExempt, String minimum, String description) {
            Asset asset = market(ticker, name, AssetType.FIXED_INCOME, RiskLevel.LOW, minimum, "Renda fixa", liquidity, description);
            asset.setRateType(rateType);
            asset.setRateValue(new BigDecimal(rateValue));
            asset.setTaxExempt(taxExempt);
            asset.setMinimumInvestment(new BigDecimal(minimum));
            return asset;
        }
    }
}
