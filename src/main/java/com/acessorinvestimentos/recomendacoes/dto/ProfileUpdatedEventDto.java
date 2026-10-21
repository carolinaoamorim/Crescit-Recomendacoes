package com.acessorinvestimentos.recomendacoes.dto;

import com.acessorinvestimentos.recomendacoes.entity.InvestorType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdatedEventDto {
    private String eventType;
    private String userId;
    private InvestorType investorType;
    private Instant occurredAt;
}
