package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.resource.InterruptionRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InterruptionService {

    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();

    public void interruption(byte[] interruptionRequestBytes) {
        InterruptionRequest interruptionRequest = jsonApiConverter.fromJsonMessage(interruptionRequestBytes, InterruptionRequest.class);
        String taskId = interruptionRequest.getId();
        Optional<Thread> thread = isRunning(taskId);
        while (thread.isPresent()) {
            thread.get().interrupt();
            thread = isRunning(taskId);
        }
    }

    private Optional<Thread> isRunning(String id) {
        return Thread.getAllStackTraces()
            .keySet()
            .stream()
            .filter(t -> t.getName().equals(id))
            .findFirst();
    }
}
