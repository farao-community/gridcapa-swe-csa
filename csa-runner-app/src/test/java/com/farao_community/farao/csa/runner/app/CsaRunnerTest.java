package com.farao_community.farao.csa.runner.app;

import com.farao_community.farao.csa.runner.api.JsonApiConverter;
import com.farao_community.farao.csa.runner.api.resource.CsaResponse;
import com.farao_community.farao.csa.runner.api.resource.Status;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.app.CsaRunner;
import com.google.common.base.Suppliers;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.ImportConfig;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.cloud.stream.function.StreamBridge;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

public class CsaRunnerTest {

    @Test
    void testLaunchCsaRequest() throws IOException {
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        byte[] requestBytes = getClass().getResourceAsStream("/csaRequestMessage.json").readAllBytes();
        RaoRunnerClient raoRunnerClient = Mockito.mock(RaoRunnerClient.class);
        MinioAdapter minioAdapter = Mockito.mock(MinioAdapter.class);
        StreamBridge streamBridge = Mockito.mock(StreamBridge.class);
        CsaRunner csaRunner = Mockito.spy(new CsaRunner(raoRunnerClient, minioAdapter, streamBridge));
        Network network = Network.read(Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString()), LocalComputationManager.getDefault(), Suppliers.memoize(ImportConfig::load).get(), new Properties());

        Mockito.doNothing().when(csaRunner).zipDataFile(any(), any());
        Mockito.doReturn(network).when(csaRunner).importNetwork(any());
        Mockito.doReturn(null).when(csaRunner).importCrac(any(), any(), any());
        Mockito.doNothing().when(minioAdapter).uploadArtifact(any(), any());
        Mockito.doReturn("presignedUrl").when(minioAdapter).generatePreSignedUrl(any());
        Mockito.doReturn(true).when(streamBridge).send(any(), any());
        Mockito.doReturn(new RaoResponse("raoResponseId", "2023-08-08T15:30:00Z", "networkWithPraFileUrl", "cracFileUrl", "raoResultFileUrl", Instant.parse("2023-08-08T15:30:00Z"), Instant.parse("2023-08-08T15:50:00Z"))).when(raoRunnerClient).runRao(any());

        byte[] result = csaRunner.launchCsaRequest(requestBytes);

        Mockito.verify(csaRunner, Mockito.times(39)).zipDataFile(any(), any());
        Mockito.verify(minioAdapter).uploadArtifact(ArgumentMatchers.eq("id/networks/20230808_1530.xiidm"), any());
        Mockito.verify(minioAdapter).generatePreSignedUrl(ArgumentMatchers.eq("id/networks/20230808_1530.xiidm"));
        Mockito.verify(minioAdapter).uploadArtifact(ArgumentMatchers.eq("id/cracs/20230808_1530.json"), any());
        Mockito.verify(minioAdapter).generatePreSignedUrl(ArgumentMatchers.eq("id/cracs/20230808_1530.json"));
        CsaResponse expectedCsaResponse = new CsaResponse("raoResponseId", Status.FINISHED.toString());
        CsaResponse recievedCsaResponse = jsonApiConverter.fromJsonMessage(result, CsaResponse.class);
        assertEquals(expectedCsaResponse.getId(), recievedCsaResponse.getId());
        assertEquals(expectedCsaResponse.getStatus(), recievedCsaResponse.getStatus());
    }
}
