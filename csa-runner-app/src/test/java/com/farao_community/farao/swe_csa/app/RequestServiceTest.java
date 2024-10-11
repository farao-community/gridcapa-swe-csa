package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class RequestServiceTest {

    @MockBean
    SweCsaRunner sweCsaRunner;
    @MockBean
    StreamBridge streamBridge;
    @Autowired
    RequestService requestService;

    @Test
    void testInterruptionRequestService() throws IOException {
        String id = UUID.randomUUID().toString();
        Exception except = new RuntimeException(new InterruptedException("interrupted"));
        String businessTimestamp = "2023-08-08T15:30:00Z";
        String gridModelUri = "https://example.com/gridModel";
        String cracFileUri = "https://example.com/crac";
        String resultsUri = "https://example.com/results";

        CsaRequest csaRequest = new CsaRequest(id, businessTimestamp, gridModelUri, cracFileUri, resultsUri);
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        CsaResponse csaResponse = new CsaResponse(csaRequest.getId(), Status.INTERRUPTED.toString(), "");
        byte[] req = jsonApiConverter.toJsonMessage(csaRequest, CsaRequest.class);
        byte[] resp = jsonApiConverter.toJsonMessage(csaResponse, CsaResponse.class);
        when(sweCsaRunner.run(any())).thenThrow(except);
        when(streamBridge.send(any(), any())).thenReturn(true);
        byte[] result = requestService.launchCsaRequest(req);
        assertArrayEquals(resp, result);
    }

    @Test
    void testRequestServiceOnRaoError() throws IOException {
        String id = UUID.randomUUID().toString();
        Exception except = new IOException("Mocked exception");
        String businessTimestamp = "2023-08-08T15:30:00Z";
        String gridModelUri = "https://example.com/gridModel";
        String cracFileUri = "https://example.com/crac";
        String resultsUri = "https://example.com/results";

        CsaRequest csaRequest = new CsaRequest(id, businessTimestamp, gridModelUri, cracFileUri, resultsUri);
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        byte[] req = jsonApiConverter.toJsonMessage(csaRequest, CsaRequest.class);
        byte[] resp = jsonApiConverter.toJsonMessage(new CsaInvalidDataException(id, "Exception happened", new InvocationTargetException(except)));
        when(sweCsaRunner.run(any())).thenThrow(except);
        when(streamBridge.send(any(), any())).thenReturn(true);
        byte[] result = requestService.launchCsaRequest(req);
        assertArrayEquals(resp, result);
    }
}
