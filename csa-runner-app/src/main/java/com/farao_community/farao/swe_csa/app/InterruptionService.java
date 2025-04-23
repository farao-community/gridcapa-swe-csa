package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.resource.InterruptionRequest;
import org.slf4j.Logger;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class InterruptionService {

    private static final String STOP_RAO_BINDING = "stop-rao-runner";
    private final StreamBridge streamBridge;
    private final Logger businessLogger;
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();

    private final Set<String> tasksToInterrupt;

    public InterruptionService(StreamBridge streamBridge, Logger businessLogger) {
        this.streamBridge = streamBridge;
        this.businessLogger = businessLogger;
        this.tasksToInterrupt = new HashSet<>();
    }

    public void interruption(byte[] interruptionRequestBytes) {
        InterruptionRequest interruptionRequest = jsonApiConverter.fromJsonMessage(interruptionRequestBytes, InterruptionRequest.class);
        String taskId = interruptionRequest.id();
        businessLogger.info("Csa run interruption asked, finding RAO runners for stopping...");
        streamBridge.send(STOP_RAO_BINDING, taskId);
        tasksToInterrupt.add(taskId);
    }

    public Set<String> getTasksToInterrupt() {
        return tasksToInterrupt;
    }
}
