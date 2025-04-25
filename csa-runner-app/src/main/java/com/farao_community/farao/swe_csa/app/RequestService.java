package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.exception.AbstractCsaException;
import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;

import com.farao_community.farao.swe_csa.app.dichotomy.FinalResult;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyRunner;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class RequestService {
    private static final String ACK_BRIDGE_NAME = "acknowledgement";
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();
    private final StreamBridge streamBridge;
    private final DichotomyRunner dichotomyRunner;
    private final S3ArtifactsAdapter s3ArtifactsAdapter;
    private final Logger businessLogger;

    public RequestService(StreamBridge streamBridge, DichotomyRunner dichotomyRunner, S3ArtifactsAdapter s3ArtifactsAdapter, Logger businessLogger) {
        this.streamBridge = streamBridge;
        this.dichotomyRunner = dichotomyRunner;
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
        this.businessLogger = businessLogger;
    }

    public byte[] launchCsaRequest(byte[] req) {
        byte[] resultBytes;
        CsaRequest csaRequest = jsonApiConverter.fromJsonMessage(req, CsaRequest.class);
        MDC.put("gridcapaTaskId", csaRequest.getId());
        try {
            String requestId = csaRequest.getId();
            // send ack message
            streamBridge.send(ACK_BRIDGE_NAME, jsonApiConverter.toJsonMessage(new CsaResponse(requestId, Status.ACCEPTED.toString(),  "",  Status.ACCEPTED.toString(), ""), CsaResponse.class));
            businessLogger.info("Csa request received : {}", csaRequest);

            //Log current version: implementation-Version from META-INF/MANIFEST.MF isn’t available in unit tests because tests run from the target/classes directory, not the actual packaged JAR
            businessLogger.info("Current CSA runner version is: {}", Optional.ofNullable(this.getClass().getPackage().getImplementationVersion()).orElse("unknown"));

            Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
            String ptEsRaoResultDestinationPath = s3ArtifactsAdapter.createRaoResultDestination(OffsetDateTime.ofInstant(utcInstant, ZoneId.of("UTC")).toString(), "PT-ES");
            String frEsRaoResultDestinationPath = s3ArtifactsAdapter.createRaoResultDestination(OffsetDateTime.ofInstant(utcInstant, ZoneId.of("UTC")).toString(), "FR-ES");

            FinalResult result = dichotomyRunner.runDichotomy(csaRequest, ptEsRaoResultDestinationPath, frEsRaoResultDestinationPath);
            businessLogger.info("CSA computation finished for TimeStamp: '{}'", utcInstant);
            CsaResponse csaResponse = new CsaResponse(csaRequest.getId(), result.getPtEsResult().getRight().toString(), s3ArtifactsAdapter.generatePreSignedUrl(ptEsRaoResultDestinationPath), result.getFrEsResult().getRight().toString(), s3ArtifactsAdapter.generatePreSignedUrl(frEsRaoResultDestinationPath));
            resultBytes = jsonApiConverter.toJsonMessage(csaResponse, CsaResponse.class);
            businessLogger.info("Csa response sent: {}", csaResponse);
        } catch (Exception e) {
            AbstractCsaException csaException = new CsaInvalidDataException(MDC.get("gridcapaTaskId"), "Exception happened", e);
            businessLogger.error(csaException.getDetails(), csaException);
            resultBytes = jsonApiConverter.toJsonMessage(csaException);
        }
        return resultBytes;
    }

}
