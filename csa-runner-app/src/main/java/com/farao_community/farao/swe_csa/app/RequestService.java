package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.exception.AbstractCsaException;
import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.api.results.ThreadLauncherResult;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RequestService {
    private static final String ACK_BRIDGE_NAME = "acknowledgement";
    private static final String STOP_RAO_BINDING = "stop-rao-runner";
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();
    private final StreamBridge streamBridge;
    private final SweCsaRunner sweCsaRunner;
    private final Logger businessLogger;

    public RequestService(StreamBridge streamBridge, SweCsaRunner sweCsaRunner, Logger businessLogger) {
        this.streamBridge = streamBridge;
        this.sweCsaRunner = sweCsaRunner;
        this.businessLogger = businessLogger;
    }

    public byte[] launchCsaRequest(byte[] req) {
        byte[] resultBytes;
        CsaRequest csaRequest = jsonApiConverter.fromJsonMessage(req, CsaRequest.class);
        MDC.put("gridcapaTaskId", csaRequest.getId());
        try {
            String requestId = csaRequest.getId();
            // send ack message
            streamBridge.send(ACK_BRIDGE_NAME, jsonApiConverter.toJsonMessage(new CsaResponse(requestId, Status.ACCEPTED.toString(), ""), CsaResponse.class));
            GenericThreadLauncher<SweCsaRunner, CsaResponse> launcher = new GenericThreadLauncher<>(sweCsaRunner, csaRequest.getId(), csaRequest);
            launcher.start();
            ThreadLauncherResult<CsaResponse> csaResponse = launcher.getResult();
            if (csaResponse.hasError() && csaResponse.getException() != null) {
                throw csaResponse.getException();
            }
            Optional<CsaResponse> resp = csaResponse.getResult();

            if (resp.isPresent() && !csaResponse.hasError()) {
                resultBytes  = jsonApiConverter.toJsonMessage(resp.get(), CsaResponse.class);
                businessLogger.info("Csa response sent: {}", resp.get());
            } else {
                businessLogger.info("Csa run is interrupted, stopping RAO runners...");
                streamBridge.send(STOP_RAO_BINDING, csaRequest.getId());
                resultBytes = jsonApiConverter.toJsonMessage(new CsaResponse(csaRequest.getId(), Status.INTERRUPTED.toString(), ""), CsaResponse.class);
            }
        } catch (Exception e) {
            AbstractCsaException csaException = new CsaInvalidDataException(MDC.get("gridcapaTaskId"), "Exception happened", e);
            businessLogger.error(csaException.getDetails(), csaException);
            resultBytes = jsonApiConverter.toJsonMessage(csaException);
        }
        return resultBytes;
    }

}
