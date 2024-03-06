package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.dichotomy.SweCsaDichotomyRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class RequestServiceTest {

    @MockBean
    SweCsaRunner sweCsaRunner;
    @MockBean
    SweCsaDichotomyRunner sweCsaDichotomyRunner;
    @MockBean
    StreamBridge streamBridge;
    @Autowired
    RequestService requestService;

    @Test
    void testInterruptionRequestService() throws IOException {
        String id = UUID.randomUUID().toString();
        Exception except = new InterruptedIOException("interrupted");
        String businessTimestamp = "2023-08-08T15:30:00Z";
        CsaRequest.CommonProfiles commonProfiles = new CsaRequest.CommonProfiles();
        commonProfiles.setTpbdProfileUri("https://example.com/tpbd");
        commonProfiles.setEqbdProfileUri("https://example.com/eqbd");
        commonProfiles.setSvProfileUri("https://example.com/sv");
        CsaRequest.Profiles frProfiles = new CsaRequest.Profiles();
        frProfiles.setSshProfileUri("https://example.com/ssh");

        String resultsUri = "https://example.com/results";

        CsaRequest csaRequest = new CsaRequest(id, businessTimestamp, commonProfiles, frProfiles, null, null, resultsUri);
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        CsaResponse csaResponse = new CsaResponse(csaRequest.getId(), Status.INTERRUPTED.toString());
        byte[] req = jsonApiConverter.toJsonMessage(csaRequest, CsaRequest.class);
        byte[] resp = jsonApiConverter.toJsonMessage(csaResponse, CsaResponse.class);
        when(sweCsaRunner.runSingleRao(any())).thenThrow(except);
        when(streamBridge.send(any(), any())).thenReturn(true);
        byte[] result = requestService.launchCsaRequest(req);
        assertArrayEquals(resp, result);
    }

    @Test
    void testRequestServiceOnRaoError() throws IOException {
        String id = UUID.randomUUID().toString();
        Exception except = new InterruptedIOException("otherError");
        String businessTimestamp = "2023-08-08T15:30:00Z";
        CsaRequest.CommonProfiles commonProfiles = new CsaRequest.CommonProfiles();
        commonProfiles.setTpbdProfileUri("https://example.com/tpbd");
        commonProfiles.setEqbdProfileUri("https://example.com/eqbd");
        commonProfiles.setSvProfileUri("https://example.com/sv");
        CsaRequest.Profiles frProfiles = new CsaRequest.Profiles();
        frProfiles.setSshProfileUri("https://example.com/ssh");

        String resultsUri = "https://example.com/results";

        CsaRequest csaRequest = new CsaRequest(id, businessTimestamp, commonProfiles, frProfiles, null, null, resultsUri);
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        byte[] req = jsonApiConverter.toJsonMessage(csaRequest, CsaRequest.class);
        CsaResponse csaResponse = new CsaResponse(csaRequest.getId(), Status.ERROR.toString());
        byte[] resp = jsonApiConverter.toJsonMessage(csaResponse, CsaResponse.class);
        when(sweCsaDichotomyRunner.runRaoDichotomy(any())).thenThrow(except);
        when(streamBridge.send(any(), any())).thenReturn(true);
        byte[] result = requestService.launchCsaRequest(req);
        assertArrayEquals(resp, result);
    }
}
