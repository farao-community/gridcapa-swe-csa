package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest
class SweCsaRunnerTest {

    @MockBean
    MinioAdapter minioAdapter;
    @MockBean
    RaoRunnerClient raoRunnerClient;
    @MockBean
    StreamBridge streamBridge;

    @Test
    void testImportCrac() {
        RaoRunnerClient raoRunnerClient = mock(RaoRunnerClient.class);
        MinioAdapter minioAdapter = mock(MinioAdapter.class);

        SweCsaRunner sweCsaRunner = new SweCsaRunner(raoRunnerClient, minioAdapter);

        Path filePath = Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString());
        Network network = sweCsaRunner.importNetwork(Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString()));

        Crac crac = sweCsaRunner.importCrac(filePath, network, Instant.parse("2023-08-08T15:30:00Z"));

        assertEquals(1, crac.getContingencies().size());
        assertEquals(6, crac.getFlowCnecs().size());
        assertEquals(3, crac.getStates().size());
    }

    // FIXME
    /*

    @Test
    void testLaunchCsaRequest() throws IOException {

        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        byte[] requestBytes = getClass().getResourceAsStream("/csaRequestMessage.json").readAllBytes();
        Network network = Network.read(Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString()), LocalComputationManager.getDefault(), Suppliers.memoize(ImportConfig::load).get(), new Properties());
        SweCsaRunner sweCsaRunner = Mockito.mock(SweCsaRunner.class);
        doNothing().when(sweCsaRunner).zipDataFile(any(), any());
        doReturn(network).when(sweCsaRunner).importNetwork(any());
        doReturn(null).when(sweCsaRunner).importCrac(any(), any(), any());
        doNothing().when(minioAdapter).uploadArtifact(any(), any());

        doReturn("presignedUrl").when(minioAdapter).generatePreSignedUrl(any());
        when(streamBridge.send(any(), any())).thenReturn(true);
        doReturn(new RaoResponse("raoResponseId", "2023-08-08T15:30:00Z", "networkWithPraFileUrl", "cracFileUrl", "raoResultFileUrl", Instant.parse("2023-08-08T15:30:00Z"), Instant.parse("2023-08-08T15:50:00Z"))).when(raoRunnerClient).runRao(any());

        CsaResponse csaResponse = sweCsaRunner.run(jsonApiConverter.fromJsonMessage(requestBytes, CsaRequest.class));
        CsaResponse expectedCsaResponse = new CsaResponse("raoResponseId", Status.FINISHED.toString());
        assertEquals(expectedCsaResponse.getId(), csaResponse.getId());
        assertEquals(expectedCsaResponse.getStatus(), csaResponse.getStatus());
    }

     */
}
