package com.acessorinvestimentos.recomendacoes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange investmentEventsExchange(@Value("${app.rabbitmq.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Queue profileUpdatedQueue(@Value("${app.rabbitmq.queues.profile-updated}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Binding profileUpdatedBinding(
            Queue profileUpdatedQueue,
            TopicExchange investmentEventsExchange,
            @Value("${app.rabbitmq.routing-keys.profile-updated}") String routingKey) {
        return BindingBuilder.bind(profileUpdatedQueue).to(investmentEventsExchange).with(routingKey);
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
