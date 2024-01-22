package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.github.jsonldjava.shaded.com.google.common.base.Suppliers;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.ImportConfig;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest
class SweCsaRunnerTest {

    @Autowired
    SweCsaRunner sweCsaRunner;

    @MockBean
    FileHelper fileHelper;

    @MockBean
    RaoRunnerClient raoRunnerClient;

    @MockBean
    StreamBridge streamBridge;

    @Test
    void testLaunchCsaRequest() throws IOException {

        try (MockedStatic<ZipHelper> utilities = Mockito.mockStatic(ZipHelper.class)) {
            utilities.when(() -> ZipHelper.zipDataCsaRequestFiles(any(), any())).thenAnswer(invocation -> null);

            JsonApiConverter jsonApiConverter = new JsonApiConverter();
            byte[] requestBytes = getClass().getResourceAsStream("/csaRequestMessage.json").readAllBytes();

            Network network = Network.read(
                Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString()),
                LocalComputationManager.getDefault(),
                Suppliers.memoize(ImportConfig::load).get(),
                new Properties()
            );
            doReturn(network).when(fileHelper).importNetwork(any());
            doReturn(null).when(fileHelper).importCrac(any(), any(), any());
            when(streamBridge.send(any(), any())).thenReturn(true);
            doReturn(new RaoResponse("raoResponseId", "2023-08-08T15:30:00Z", "networkWithPraFileUrl", "cracFileUrl", "raoResultFileUrl", Instant.parse("2023-08-08T15:30:00Z"), Instant.parse("2023-08-08T15:50:00Z"))).when(raoRunnerClient).runRao(any());

            CsaRequest csaRequest = jsonApiConverter.fromJsonMessage(requestBytes, CsaRequest.class);
            CsaResponse csaResponse = sweCsaRunner.run(csaRequest);

            CsaResponse expectedCsaResponse = new CsaResponse("raoResponseId", Status.FINISHED.toString());
            assertEquals(expectedCsaResponse.getId(), csaResponse.getId());
            assertEquals(expectedCsaResponse.getStatus(), csaResponse.getStatus());
        }

    }

}
