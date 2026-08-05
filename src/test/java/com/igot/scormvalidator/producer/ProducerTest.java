package com.igot.scormvalidator.producer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private Producer producer;

    private Map<String, Object> event;

    @BeforeEach
    void setUp() {
        event = new LinkedHashMap<>();
    }

    @Test
    void pushSerializesValueAndSendsWithoutKey() {
        event.put("status", "STARTED");

        producer.push("some.topic", event);

        verify(kafkaTemplate).send(eq("some.topic"), anyString());
    }

    @Test
    void pushWithKeySerializesValueAndSendsWithKey() {
        event.put("status", "COMPLETED");

        producer.pushWithKey("some.topic", event, "resource-1");

        verify(kafkaTemplate).send(eq("some.topic"), eq("resource-1"), anyString());
    }

    @Test
    void pushSwallowsExceptionFromKafkaTemplate() {
        when(kafkaTemplate.send(anyString(), anyString())).thenThrow(new RuntimeException("broker unreachable"));
        event.put("status", "STARTED");

        assertDoesNotThrow(() -> producer.push("some.topic", event));

        verify(kafkaTemplate).send(eq("some.topic"), anyString());
    }

    @Test
    void pushWithKeySwallowsExceptionFromKafkaTemplate() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("broker unreachable"));
        event.put("status", "FAILED");

        assertDoesNotThrow(() -> producer.pushWithKey("some.topic", event, "resource-1"));

        verify(kafkaTemplate).send(eq("some.topic"), eq("resource-1"), anyString());
    }

    @Test
    void pushDoesNotCallSendWhenValueIsNotSerializable() {
        producer.push("some.topic", new NotSerializable());

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    private static final class NotSerializable {
        @SuppressWarnings("unused")
        public NotSerializable getSelf() {
            return this;
        }
    }
}
