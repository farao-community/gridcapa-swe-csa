package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest
class SweCsaRunnerTest {

    @Autowired
    SweCsaRunner sweCsaRunner;

    @MockBean
    FileImporter fileImporter;

    @MockBean
    RaoRunnerClient raoRunnerClient;

    @MockBean
    StreamBridge streamBridge;

    @Test
    void testLaunchCsaRequest() throws IOException {

        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        byte[] requestBytes = getClass().getResourceAsStream("/csaRequestMessage.json").readAllBytes();

        Network network = Network.read(getClass().getResource("/rao_inputs/network.xiidm").getPath());
        doReturn(network).when(fileImporter).importNetwork(any());
        doReturn(null).when(fileImporter).importCrac(any());
        when(streamBridge.send(any(), any())).thenReturn(true);
        doReturn(new RaoResponse.RaoResponseBuilder().withId("raoResponseId")
            .withInstant("2023-08-08T15:30:00Z")
            .withNetworkWithPraFileUrl("networkWithPraFileUrl")
            .withCracFileUrl("cracFileUrl")
            .withRaoResultFileUrl("raoResultFileUrl")
            .withComputationStartInstant(Instant.parse("2023-08-08T15:30:00Z"))
            .withComputationEndInstant(Instant.parse("2023-08-08T15:50:00Z")).build())
            .when(raoRunnerClient).runRao(any());

        CsaRequest csaRequest = jsonApiConverter.fromJsonMessage(requestBytes, CsaRequest.class);
        CsaResponse csaResponse = sweCsaRunner.run(csaRequest);

        CsaResponse expectedCsaResponse = new CsaResponse("raoResponseId", Status.FINISHED.toString());
        assertEquals(expectedCsaResponse.getId(), csaResponse.getId());
        assertEquals(expectedCsaResponse.getStatus(), csaResponse.getStatus());
    }

}
