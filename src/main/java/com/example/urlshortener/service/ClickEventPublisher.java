package com.example.urlshortener.service;

import com.example.urlshortener.dto.ClickEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ClickEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${urlshortener.exchange}")
    private String exchange;

    @Value("${urlshortener.queue.clickEvents}")
    private String queue;

    @Value("${urlshortener.routingKey.clickEvents}")
    private String routingKey;

    public ClickEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes a click event to the RabbitMQ queue for asynchronous processing.
     *
     * This method demonstrates the **fire-and-forget** asynchronous pattern:
     *
     * 1. **Decoupling**: The redirect service does not wait for the analytics
     *    processing to complete. It simply publishes the event and returns
     *    immediately to the user.
     *
     * 2. **Failure Isolation**: If the analytics processing is slow or fails,
     *    it does not affect the redirect service's responsiveness.
     *
     * 3. **Eventual Consistency**: The click event will be processed by the
     *    consumer at some point after being published. There may be a short
     *    delay between the click and its persistence in the analytics store.
     *
     * 4. **Reliability**: RabbitMQ provides durability and persistence options
     *    to ensure that events are not lost even if the consumer is temporarily
     *    unavailable.
     *
     * Fire-and-forget trade-offs:
     *    - Pros: Low latency for the user, fault isolation, scalability.
     *    - Cons: No immediate feedback if the event processing fails (we handle
     *      this via dead letter queues and monitoring in the consumer).
     *
     * In this project:
     *    - We use a direct exchange for simple routing.
     *    - The message payload is a ClickEventDto containing the minimal
     *      information needed for analytics: shortCode, timestamp, IP address,
     *      user agent, and referrer.
     *    - We do not include the full UrlMapping object to avoid coupling
     *      the analytics service to the URL mapping schema and to reduce
     *      message size.
     *    - We rely on the consumer to persist the click event to the database
     *      and handle duplicates (idempotency) if needed.
     */
    public void publishClickEvent(ClickEventDto clickEvent) {
        // We use the convertAndSend method to convert the DTO to JSON and send it
        // to the specified exchange and routing key.
        // The message will be routed to the queue bound to the exchange with
        // the matching routing key.
        rabbitTemplate.convertAndSend(exchange, routingKey, clickEvent, message -> {
            // We can set message properties here if needed, such as making the
            // message persistent.
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });
    }
}