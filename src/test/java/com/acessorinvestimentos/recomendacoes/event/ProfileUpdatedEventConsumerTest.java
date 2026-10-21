package com.acessorinvestimentos.recomendacoes.event;

import com.acessorinvestimentos.recomendacoes.dto.ProfileUpdatedEventDto;
import com.acessorinvestimentos.recomendacoes.entity.InvestorType;
import com.acessorinvestimentos.recomendacoes.service.RecommendationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ProfileUpdatedEventConsumerTest {

    private final RecommendationService service = mock(RecommendationService.class);
    private final ProfileUpdatedEventConsumer consumer = new ProfileUpdatedEventConsumer(service);

    @Test
    void shouldGenerateUsingDataAlreadyPresentInEvent() {
        ProfileUpdatedEventDto event = new ProfileUpdatedEventDto(
                "PROFILE_UPDATED", "auth0|123", InvestorType.MODERATE, Instant.parse("2026-10-21T12:00:00Z"));

        consumer.consume(event);

        verify(service).generateRecommendations("auth0|123", InvestorType.MODERATE);
    }

    @Test
    void shouldIgnoreInvalidEvent() {
        consumer.consume(new ProfileUpdatedEventDto(
                "OTHER_EVENT", "auth0|123", InvestorType.MODERATE, Instant.now()));

        verifyNoInteractions(service);
    }
}
