package com.acessorinvestimentos.recomendacoes.event;

import com.acessorinvestimentos.recomendacoes.dto.ProfileUpdatedEventDto;
import com.acessorinvestimentos.recomendacoes.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileUpdatedEventConsumer {

    private final RecommendationService recommendationService;

    @RabbitListener(queues = "${app.rabbitmq.queues.profile-updated}")
    public void consume(ProfileUpdatedEventDto event) {
        if (event != null
                && "PROFILE_UPDATED".equals(event.getEventType())
                && event.getUserId() != null
                && !event.getUserId().isBlank()
                && event.getInvestorType() != null) {
            recommendationService.generateRecommendations(event.getUserId(), event.getInvestorType());
        }
    }
}
