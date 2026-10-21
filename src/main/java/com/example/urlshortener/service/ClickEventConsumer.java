package com.example.urlshortener.service;

import com.example.urlshortener.dto.ClickEventDto;
import com.example.urlshortener.entity.ClickEvent;
import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ClickEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventConsumer.class);

    private final ClickEventRepository clickEventRepository;
    private final UrlMappingRepository urlMappingRepository;

    public ClickEventConsumer(ClickEventRepository clickEventRepository, UrlMappingRepository urlMappingRepository) {
        this.clickEventRepository = clickEventRepository;
        this.urlMappingRepository = urlMappingRepository;
    }

    /**
     * Listens for click events on the RabbitMQ queue and persists them to the database.
     *
     * This method demonstrates several reliability patterns:
     *
     * 1. **Idempotency**:
     *    - We rely on a unique database constraint on (url_id, clickedAt, ipAddress)
     *      to prevent duplicate click events from being inserted.
     *    - If a duplicate message is received (due to network retries, for example),
     *      the database will reject the insert, and we log the event but do not
     *      treat it as a failure.
     *    - This ensures that each unique click (defined by URL, timestamp, and IP)
     *      is recorded exactly once, even in the presence of retries.
     *
     * 2. **Error Handling**:
     *    - We catch exceptions and log them, allowing the message to be
     *      rejected and potentially routed to a dead letter queue (if configured).
     *    - In a production system, we would configure a dead letter exchange
     *      and set a maximum number of retries to avoid infinite retry loops.
     *
     * 3. **Minimal Processing**:
     *    - We keep the consumer logic simple and fast to achieve high throughput.
     *    - We do not perform complex analytics in the consumer; we simply
     *      persist the raw event. Analytics can be computed later via batch
     *      processes or stream processing.
     *
     * 4. **Batch Processing Considerations**:
     *    - Although we process messages one at a time here, we could
     *      configure the RabbitMQ listener to batch messages for improved
     *      throughput at the cost of slightly higher latency.
     *    - The choice between individual and batch processing depends on
     *      the required throughput and latency constraints.
     *
     * 5. **Poison Message Handling**:
     *    - If a message is malformed (e.g., invalid JSON, missing required fields),
     *      the conversion will fail and the message will be logged.
     *      Depending on the container factory configuration, it may be
     *      rejected and sent to a dead letter queue.
     *    - We assume that the publisher only sends valid messages, but we
     *      still handle exceptions gracefully.
     *
     * In this project:
     *    - We use the `@RabbitListener` annotation to simplify the creation
     *      of a message listener.
     *    - We manually acknowledge messages (the default container factory
     *      uses acknowledgment mode) to ensure that messages are only
     *      removed from the queue after successful processing.
     *    - We do not use transactions between the RabbitMQ receive and the
     *      database insert, relying instead on the unique constraint for
     *      idempotency. This is acceptable because the window of inconsistency
     *      is small and duplicates are harmless.
     */
    @RabbitListener(queues = "${urlshortener.queue.clickEvents}")
    public void consumeClickEvent(ClickEventDto clickEventDto) {
        try {
            // Look up the URL mapping by short code to get the URL ID
            UrlMapping urlMapping = urlMappingRepository.findByShortCode(clickEventDto.getShortCode());
            if (urlMapping == null) {
                // This should not happen if the publisher only publishes for existing URLs,
                // but we handle it gracefully.
                log.warn("Received click event for unknown short code: {}", clickEventDto.getShortCode());
                return;
            }

            // Create the ClickEvent entity
            ClickEvent clickEvent = new ClickEvent();
            clickEvent.setUrlMapping(urlMapping);
            clickEvent.setClickedAt(clickEventDto.getClickedAt());
            clickEvent.setIpAddress(clickEventDto.getIpAddress());
            clickEvent.setUserAgent(clickEventDto.getUserAgent());
            clickEvent.setReferrer(clickEventDto.getReferrer());
            clickEvent.setEventId(clickEventDto.getEventId());

            // Save the click event to the database.
            // A unique constraint on event_id prevents duplicate processing.
            try {
                clickEventRepository.save(clickEvent);
                log.debug("Persisted click event for short code: {}", clickEventDto.getShortCode());
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate click event ignored for eventId: {}", clickEventDto.getEventId());
            }
        } catch (Exception e) {
            // Log the error and let the message be rejected (or handled by the retry mechanism)
            log.error("Failed to process click event: {}", clickEventDto, e);
            // In a production system, we might want to increment a metric or send an alert
            // after a certain number of failures.
        }
    }
}