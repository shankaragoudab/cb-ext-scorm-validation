package com.igot.scormvalidator.consumer;

import com.igot.scormvalidator.util.Constants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScormValidationConsumerTest {

    @Mock
    private CassandraOperation cassandraOperation;

    @InjectMocks
    private ScormValidationConsumer consumer;

    @Test
    void processScormValidationRequestedDoesNothingForBlankMessage() {
        consumer.processScormValidationRequested(new ConsumerRecord<>("topic", 0, 0L, "key", ""));

        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void processScormValidationRequestedDispatchesProcessingAsynchronously() {
        lenient().when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());

        consumer.processScormValidationRequested(
                new ConsumerRecord<>("topic", 0, 0L, "resource-1", "{\"resourceId\":\"resource-1\"}"));

        verify(cassandraOperation, timeout(2000).times(3)).updateRecord(anyString(), anyString(), anyMap(), anyMap());
    }

    @Test
    void initiateScormValidationProcessDoesNothingForUnparsableJson() {
        consumer.initiateScormValidationProcess("not-json");

        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void initiateScormValidationProcessDoesNothingWhenResourceIdMissing() {
        consumer.initiateScormValidationProcess("{\"validationId\":\"v-1\"}");

        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void initiateScormValidationProcessTransitionsThroughInProgressValidCompleted() {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());

        consumer.initiateScormValidationProcess("{\"resourceId\":\"resource-1\"}");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(3))
                .updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_IN_PROGRESS, updates.get(0).get(Constants.STATUS));
        assertEquals(Constants.STATUS_VALID, updates.get(1).get(Constants.STATUS));
        assertEquals(Constants.STATUS_COMPLETED, updates.get(2).get(Constants.STATUS));
    }

    @Test
    void initiateScormValidationProcessMarksFailedWhenUpdateThrows() {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap()))
                .thenThrow(new RuntimeException("cassandra down"))
                .thenReturn(successResponse());

        consumer.initiateScormValidationProcess("{\"resourceId\":\"resource-1\"}");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(2))
                .updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_IN_PROGRESS, updates.get(0).get(Constants.STATUS));
        assertEquals(Constants.STATUS_FAILED, updates.get(1).get(Constants.STATUS));
        assertEquals("cassandra down", updates.get(1).get(Constants.ERROR_REASON));
    }

    private Map<String, Object> successResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }
}
