package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyRunner;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import kotlin.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class RequestServiceTest {

    @MockBean
    DichotomyRunner dichotomyRunner;

    @MockBean
    S3ArtifactsAdapter s3ArtifactsAdapter;

    @MockBean
    StreamBridge streamBridge;

    @Autowired
    RequestService requestService;

    @Test
    void checkResultWhenCsaRunIsFinishedSecure() throws IOException, GlskLimitationException, ShiftingException {
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        byte[] requestBytes = getClass().getResourceAsStream("/csaRequestMessage.json").readAllBytes();

        when(streamBridge.send(any(), any())).thenReturn(true);

        when(s3ArtifactsAdapter.createRaoResultDestination(OffsetDateTime.ofInstant(Instant.parse("2023-08-08T15:30:00Z"), ZoneId.of("UTC")).toString())).thenReturn("resultsPath");
        when(s3ArtifactsAdapter.generatePreSignedUrl("resultsPath")).thenReturn("https://cds/resultsUri.signed.url");
        when(dichotomyRunner.runDichotomy(new CsaRequest("id", "2023-08-08T15:30:00Z", "https://cds/gridModelUri.signed.url", "https://cds/glskUri.signed.url", "https://cds/ptEsCracFileUri.signed.url", "https://cds/frEsCracFileUri.signed.url"), "resultsPath")).thenReturn(new Pair<>(null, Status.FINISHED_SECURE));

        byte[] resultBytes = requestService.launchCsaRequest(requestBytes);
        CsaResponse csaResponse = jsonApiConverter.fromJsonMessage(resultBytes, CsaResponse.class);
        CsaResponse expectedCsaResponse = new CsaResponse("id", Status.FINISHED_SECURE.toString(), "https://cds/resultsUri.signed.url");
        assertEquals(expectedCsaResponse.getId(), csaResponse.getId());
        assertEquals(expectedCsaResponse.getStatus(), csaResponse.getStatus());
        assertEquals(expectedCsaResponse.getRaoResultUri(), csaResponse.getRaoResultUri());
    }

    @Test
    void checkResultWhenCsaRunIsEncounterAnException() throws IOException, GlskLimitationException, ShiftingException, URISyntaxException {
        byte[] requestBytes = getClass().getResourceAsStream("/csaRequestMessage.json").readAllBytes();

        when(streamBridge.send(any(), any())).thenReturn(true);
        when(dichotomyRunner.runDichotomy(any(), any())).thenThrow(new RuntimeException("Invalid data exception"));
        when(s3ArtifactsAdapter.generatePreSignedUrl("https://cds/resultsUri.signed.url")).thenReturn("https://cds/resultsUri.signed.url");

        assertEquals("{\"errors\":[{\"id\":\"id\",\"links\":null,\"status\":\"400\",\"code\":\"400-InvalidDataException\",\"title\":\"Exception happened\",\"detail\":\"Exception happened; nested exception is java.lang.RuntimeException: Invalid data exception\",\"source\":null,\"meta\":null}]}",
            new String(requestService.launchCsaRequest(requestBytes)));
    }

}
