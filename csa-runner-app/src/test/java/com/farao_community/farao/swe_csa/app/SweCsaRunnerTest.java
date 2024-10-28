package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyRunner;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import kotlin.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest
class SweCsaRunnerTest {

    @Autowired
    SweCsaRunner sweCsaRunner;

    @MockBean
    FileImporter fileImporter;

    @MockBean
    DichotomyRunner dichotomyRunner;

    @MockBean
    StreamBridge streamBridge;

    @MockBean
    S3ArtifactsAdapter s3ArtifactsAdapter;

    @Test
    void testLaunchCsaRequest() throws IOException, GlskLimitationException, ShiftingException {

        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        byte[] requestBytes = getClass().getResourceAsStream("/csaRequestMessage.json").readAllBytes();

        Network network = Network.read(getClass().getResource("/rao_inputs/network.xiidm").getPath());
        doReturn(network).when(fileImporter).importNetwork(any());
        doReturn(null).when(fileImporter).importCrac(any(), any());
        when(streamBridge.send(any(), any())).thenReturn(true);
        CsaRequest csaRequest = jsonApiConverter.fromJsonMessage(requestBytes, CsaRequest.class);

        Pair<RaoResult, Status> result = new Pair<>(null, Status.FINISHED_SECURE);
        Mockito.when(dichotomyRunner.runDichotomy(csaRequest)).thenReturn(result);
        Mockito.when(s3ArtifactsAdapter.generatePreSignedUrl("https://cds/resultsUri.signed.url")).thenReturn("https://cds/resultsUri.signed.url");

        CsaResponse csaResponse = sweCsaRunner.run(csaRequest);
        CsaResponse expectedCsaResponse = new CsaResponse("id", Status.FINISHED_SECURE.toString(), "https://cds/resultsUri.signed.url");
        assertEquals(expectedCsaResponse.getId(), csaResponse.getId());
        assertEquals(expectedCsaResponse.getStatus(), csaResponse.getStatus());
        assertEquals(expectedCsaResponse.getRaoResultUri(), csaResponse.getRaoResultUri());
    }

}
