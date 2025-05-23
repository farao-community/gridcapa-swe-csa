package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.resource.InterruptionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.springframework.cloud.stream.function.StreamBridge;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class InterruptionServiceTest {

    @Mock
    private StreamBridge streamBridge;

    @Mock
    private Logger businessLogger;

    @Mock
    private JsonApiConverter jsonApiConverter;

    @InjectMocks
    private InterruptionService interruptionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testInterruption() {
        String taskId = "task1";
        byte[] interruptionRequestBytes = ("{\"data\": {\"type\": \"csa-interruption-request\", \"id\": \"" + taskId + "\"}}").getBytes(StandardCharsets.UTF_8);

        InterruptionRequest interruptionRequest = mock(InterruptionRequest.class);
        when(interruptionRequest.getId()).thenReturn(taskId);
        when(jsonApiConverter.fromJsonMessage(interruptionRequestBytes, InterruptionRequest.class)).thenReturn(interruptionRequest);

        interruptionService.interruption(interruptionRequestBytes);

        verify(businessLogger).info("Csa run interruption asked for task {}, finding RAO runners for stopping...", taskId);
        verify(streamBridge).send("stop-rao-runner", taskId);
        Set<String> tasksToInterrupt = interruptionService.getTasksToInterrupt();
        assertTrue(tasksToInterrupt.contains(taskId));
    }

    @Test
    void testGetTasksToInterrupt() {
        String taskId1 = "task1";
        String taskId2 = "task2";
        byte[] interruptionRequest1Bytes = ("{\"data\": {\"type\": \"csa-interruption-request\", \"id\": \"" + taskId1 + "\"}}").getBytes(StandardCharsets.UTF_8);
        byte[] interruptionRequest2Bytes = ("{\"data\": {\"type\": \"csa-interruption-request\", \"id\": \"" + taskId2 + "\"}}").getBytes(StandardCharsets.UTF_8);

        interruptionService.interruption(interruptionRequest1Bytes);
        interruptionService.interruption(interruptionRequest2Bytes);

        Set<String> tasksToInterrupt = interruptionService.getTasksToInterrupt();

        assert tasksToInterrupt.size() == 2;
        assert tasksToInterrupt.contains(taskId1);
        assert tasksToInterrupt.contains(taskId2);
    }
}
