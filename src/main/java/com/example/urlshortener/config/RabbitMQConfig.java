package com.example.urlshortener.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${urlshortener.exchange}")
    private String exchangeName;

    @Value("${urlshortener.queue.clickEvents}")
    private String clickEventsQueue;

    @Value("${urlshortener.routingKey.clickEvents}")
    private String clickEventsRoutingKey;

    @Bean
    public Queue clickEventsQueue() {
        return new Queue(clickEventsQueue, true);
    }

    @Bean
    public DirectExchange urlShortenerExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Binding clickEventsBinding(
            Queue clickEventsQueue,
            DirectExchange urlShortenerExchange) {

        return BindingBuilder
                .bind(clickEventsQueue)
                .to(urlShortenerExchange)
                .with(clickEventsRoutingKey);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}