package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.exception.AbstractCsaException;
import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.api.results.ThreadLauncherResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RequestService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestService.class);
    private static final String ACK_BRIDGE_NAME = "acknowledgement";
    private static final String STOP_RAO_BINDING = "stop-rao-runner";
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();
    private final StreamBridge streamBridge;
    private final SweCsaRunner sweCsaRunner;

    public RequestService(StreamBridge streamBridge, SweCsaRunner sweCsaRunner) {
        this.streamBridge = streamBridge;
        this.sweCsaRunner = sweCsaRunner;
    }

    public byte[] launchCsaRequest(byte[] req) {
        byte[] resultBytes;
        CsaRequest csaRequest = jsonApiConverter.fromJsonMessage(req, CsaRequest.class);
        MDC.put("gridcapa-task-id", csaRequest.getId());
        try {
            String requestId = csaRequest.getId();
            // send ack message
            streamBridge.send(ACK_BRIDGE_NAME, jsonApiConverter.toJsonMessage(new CsaResponse(requestId, Status.ACCEPTED.toString()), CsaResponse.class));
            GenericThreadLauncher<SweCsaRunner, CsaResponse> launcher = new GenericThreadLauncher<>(sweCsaRunner, csaRequest.getId(), csaRequest);
            launcher.start();
            ThreadLauncherResult<CsaResponse> csaResponse = launcher.getResult();
            if (csaResponse.hasError() && csaResponse.getException() != null) {
                throw csaResponse.getException();
            }
            Optional<CsaResponse> resp = csaResponse.getResult();

            if (resp.isPresent() && !csaResponse.hasError()) {
                resultBytes  = jsonApiConverter.toJsonMessage(resp.get(), CsaResponse.class);
                LOGGER.info("Csa response sent: {}", resp.get());
            } else {
                LOGGER.info("Csa run is interrupted, stopping RAO runners...");
                streamBridge.send(STOP_RAO_BINDING, csaRequest.getId());
                // TODO read acknowledgment from rao runner to make sure rao is interrupted
                resultBytes = jsonApiConverter.toJsonMessage(new CsaResponse(csaRequest.getId(), Status.INTERRUPTED.toString()), CsaResponse.class);
            }
        } catch (Exception e) {
            AbstractCsaException csaException = new CsaInvalidDataException("Exception happened", e);
            LOGGER.error(csaException.getDetails(), csaException);
            resultBytes = jsonApiConverter.toJsonMessage(csaException);
        }
        return resultBytes;
    }

}
