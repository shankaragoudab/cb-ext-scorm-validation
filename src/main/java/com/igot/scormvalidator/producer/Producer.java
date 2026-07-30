package com.igot.scormvalidator.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Producer {

    private static final Logger logger = LoggerFactory.getLogger(Producer.class);
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void push(String topic, Object value) {
        try {
            String message = mapper.writeValueAsString(value);
            kafkaTemplate.send(topic, message);
        } catch (Exception e) {
            logger.error("Producer: failed to publish event to topic {}", topic, e);
        }
    }

    public void pushWithKey(String topic, Object value, String key) {
        try {
            String message = mapper.writeValueAsString(value);
            kafkaTemplate.send(topic, key, message);
        } catch (Exception e) {
            logger.error("Producer: failed to publish event to topic {}", topic, e);
        }
    }
}
