package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyRunner;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.io.IOException;
import java.net.URISyntaxException;

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
/*
    @Test
    void checkResultWhenCsaRunIsFinishedSecure() throws IOException, GlskLimitationException, ShiftingException {
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        byte[] requestBytes = getClass().getResourceAsStream("/csaRequestMessage.json").readAllBytes();
        CsaRequest csaRequest = new CsaRequest("id", "2023-08-08T15:30:00Z", "https://cds/gridModelUri.signed.url", "https://cds/glskUri.signed.url", "https://cds/ptEsCracFileUri.signed.url", "https://cds/frEsCracFileUri.signed.url");

        when(streamBridge.send(any(), any())).thenReturn(true);

        when(s3ArtifactsAdapter.createRaoResultDestination(OffsetDateTime.ofInstant(Instant.parse("2023-08-08T15:30:00Z"), ZoneId.of("UTC")).toString(), "PT-ES")).thenReturn("pt-es-rao-result-path");
        when(s3ArtifactsAdapter.generatePreSignedUrl("pt-es-rao-result-path")).thenReturn("https://cds/pt-es-rao-result.signed.url");
        when(s3ArtifactsAdapter.createRaoResultDestination(OffsetDateTime.ofInstant(Instant.parse("2023-08-08T15:30:00Z"), ZoneId.of("UTC")).toString(), "FR-ES")).thenReturn("fr-es-rao-result-path");
        when(s3ArtifactsAdapter.generatePreSignedUrl("fr-es-rao-result-path")).thenReturn("https://cds/fr-es-rao-result.signed.url");
        ParallelDichotomiesResult parallelDichotomiesResult = new ParallelDichotomiesResult(new Pair<>(null, Status.FINISHED_SECURE), new Pair<>(null, Status.FINISHED_SECURE), new CounterTradingValues(1000, 200));

        when(dichotomyRunner.runDichotomy(csaRequest, "pt-es-rao-result-path", "fr-es-rao-result-path")).thenReturn(parallelDichotomiesResult);

        byte[] resultBytes = requestService.launchCsaRequest(requestBytes);
        CsaResponse csaResponse = jsonApiConverter.fromJsonMessage(resultBytes, CsaResponse.class);
        CsaResponse expectedCsaResponse = new CsaResponse("id", Status.FINISHED_SECURE.toString(), "https://cds/pt-es-rao-result.signed.url", Status.FINISHED_SECURE.toString(), "https://cds/fr-es-rao-result.signed.url");
        assertEquals(expectedCsaResponse.getId(), csaResponse.getId());
        assertEquals(expectedCsaResponse.getPtEsStatus(), csaResponse.getPtEsStatus());
        assertEquals(expectedCsaResponse.getPtEsRaoResultUri(), csaResponse.getPtEsRaoResultUri());
        assertEquals(expectedCsaResponse.getFrEsStatus(), csaResponse.getFrEsStatus());
        assertEquals(expectedCsaResponse.getFrEsRaoResultUri(), csaResponse.getFrEsRaoResultUri());
    }
*/
    @Test
    void checkResultWhenCsaRunIsEncounterAnException() throws IOException, GlskLimitationException, ShiftingException, URISyntaxException {
        byte[] requestBytes = getClass().getResourceAsStream("/csaRequestMessage.json").readAllBytes();

        when(streamBridge.send(any(), any())).thenReturn(true);
        when(dichotomyRunner.runDichotomy(any(), any(), any())).thenThrow(new RuntimeException("Invalid data exception"));
        when(s3ArtifactsAdapter.generatePreSignedUrl("https://cds/resultsUri.signed.url")).thenReturn("https://cds/resultsUri.signed.url");

        assertEquals("{\"errors\":[{\"id\":\"id\",\"links\":null,\"status\":\"400\",\"code\":\"400-InvalidDataException\",\"title\":\"Exception happened\",\"detail\":\"Exception happened; nested exception is java.lang.RuntimeException: Invalid data exception\",\"source\":null,\"meta\":null}]}",
            new String(requestService.launchCsaRequest(requestBytes)));
    }

}
