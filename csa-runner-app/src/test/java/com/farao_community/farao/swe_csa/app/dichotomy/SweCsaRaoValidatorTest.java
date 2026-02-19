package com.farao_community.farao.swe_csa.app.dichotomy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import com.farao_community.farao.rao_runner.api.resource.RaoFailureResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SweCsaRaoValidatorTest {

    @Autowired
    FileImporter fileImporter;

    @MockitoBean
    RaoRunnerClient raoRunnerClient;

    @Autowired
    SweCsaRaoValidator sweCsaRaoValidator;

    @Test
    void testGetBorderFlowCnecs() {
        Network network = Network.read(getResource("/rao_inputs/network.xiidm"));
        Crac crac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/rao_inputs/crac.json")).toString(), network);

        Set<FlowCnec> cnecsPtEs = SweCsaRaoValidator.getBorderFlowCnecs(crac, "PT-ES");
        Set<FlowCnec> cnecsFrEs = SweCsaRaoValidator.getBorderFlowCnecs(crac, "FR-ES");

        assertEquals(0, cnecsPtEs.size());
        assertEquals(0, cnecsFrEs.size());
    }

    @Test
    void testGetFlowCnecShortestMargin() {
        RaoResult raoResult = Mockito.mock(RaoResult.class);
        Mockito.when(raoResult.getMargin(any(), (FlowCnec) any(), any()))
            .then(i -> {
                FlowCnec flowCnec = i.getArgument(1);
                if ("id2".equals(flowCnec.getId())) {
                    return 50.0;
                } else {
                    return 100.0;
                }
            });
        Set<FlowCnec> flowCnecs = new HashSet<>();
        FlowCnec fc1 = Mockito.mock(FlowCnec.class);
        Mockito.when(fc1.getId()).thenReturn("id1");
        FlowCnec fc2 = Mockito.mock(FlowCnec.class);
        Mockito.when(fc2.getId()).thenReturn("id2");
        FlowCnec fc3 = Mockito.mock(FlowCnec.class);
        Mockito.when(fc3.getId()).thenReturn("id3");
        flowCnecs.add(fc1);
        flowCnecs.add(fc2);
        flowCnecs.add(fc3);

        State stateMock = Mockito.mock(State.class);
        Instant instantMock = Mockito.mock(Instant.class);
        Mockito.when(stateMock.getInstant()).thenReturn(instantMock);
        Mockito.when(fc1.getState()).thenReturn(stateMock);
        Mockito.when(fc2.getState()).thenReturn(stateMock);
        Mockito.when(fc3.getState()).thenReturn(stateMock);

        Pair<String, Double> flowCnecShortestMargin = sweCsaRaoValidator.getFlowCnecSmallestMargin(raoResult, flowCnecs);
        assertEquals("id2", flowCnecShortestMargin.getLeft());
        assertEquals(50.0, flowCnecShortestMargin.getRight());
    }

    @Test
    void testValidateNetworkRaoFailureResponse() {
        Network network = Network.read(getResource("/rao_inputs/network.xiidm"));
        Crac crac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/rao_inputs/crac.json")).toString(), network);

        Mockito.when(raoRunnerClient.runRao(any())).thenReturn(new RaoFailureResponse.Builder().withId("id").withErrorMessage("errorMessage").build());
        assertThrows(CsaInternalException.class, () -> sweCsaRaoValidator.validateNetworkForPortugueseBorder(network, crac, "", null, new RaoParameters(),
            new CsaRequest("id", "2024-12-01T15:30:00Z", "", "", "", ""), "raoParametersUrl", new CounterTradingValues(0.0, 0.0)));
    }

    private Path getResource(String res) {
        try {
            return Paths.get(getClass().getResource(res).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid resource", e);
        }
    }

}
