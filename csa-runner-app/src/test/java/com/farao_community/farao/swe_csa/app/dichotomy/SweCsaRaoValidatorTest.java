package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.Instant;
import com.powsybl.openrao.data.cracapi.State;
import com.powsybl.openrao.data.cracapi.cnec.FlowCnec;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
public class SweCsaRaoValidatorTest {

    @Autowired
    FileImporter fileImporter;

    @Mock
    FileExporter fileExporter;

    @Mock
    RaoRunnerClient raoRunnerClient;

    @Test
    void testGetBorderFlowCnecs() {
        Network network = Network.read(getClass().getResource("/rao_inputs/network.xiidm").getPath());
        Crac crac = fileImporter.importCrac(Objects.requireNonNull(getClass().getResource("/rao_inputs/crac.json")).toString(), network);

        Set<FlowCnec> cnecsFr = SweCsaRaoValidator.getBorderFlowCnecs(crac, network, Country.FR);
        Set<FlowCnec> cnecsDe = SweCsaRaoValidator.getBorderFlowCnecs(crac, network, Country.DE);
        Set<FlowCnec> cnecsNl = SweCsaRaoValidator.getBorderFlowCnecs(crac, network, Country.NL);

        assertEquals(9, cnecsFr.size());
        assertEquals(4, cnecsDe.size());
        assertEquals(2, cnecsNl.size());
    }

    @Test
    void testGetFlowCnecShortestMargin() {
        SweCsaRaoValidator sweCsaRaoValidator = new SweCsaRaoValidator(fileExporter, raoRunnerClient);
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

        Pair<String, Double> flowCnecShortestMargin = sweCsaRaoValidator.getFlowCnecShortestMargin(raoResult, flowCnecs);
        assertEquals("id2", flowCnecShortestMargin.getLeft());
        assertEquals(50.0, flowCnecShortestMargin.getRight());
    }
}
