package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.AsynchronousRaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.serde.NetworkSerDe;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.cnec.FlowCnec;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import com.powsybl.openrao.raoapi.Rao;
import com.powsybl.openrao.raoapi.RaoInput;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.searchtreerao.faorao.FastRao;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class SweCsaDichotomyRunnerTest {

    @Autowired
    FileTestUtils fileTestUtils;
    @Mock
    FileImporter fileImporter;
    @Mock
    FileExporter fileExporter;
    @Mock
    AsynchronousRaoRunnerClient raoRunnerClient;

    @Test
    void launchCoresoTest() throws GlskLimitationException, ShiftingException {
        Path filePath = Paths.get(new File(getClass().getResource("/TestCase_1_16_2.zip").getFile()).toString());
        Instant utcInstant = Instant.parse("2023-09-13T09:30:00Z");
        Network network = fileTestUtils.getNetworkFromResource(filePath);
        Crac crac = fileTestUtils.importCrac(filePath, network, utcInstant);
        RaoParameters raoParameters = new RaoParameters();
        JsonRaoParameters.update(raoParameters, getClass().getResourceAsStream("/RaoParameters.json"));

        Mockito.when(fileImporter.uploadRaoParameters(utcInstant)).thenReturn("rao-parameters-url");
        Mockito.when(fileImporter.importNetwork("cgm-url")).thenReturn(network);
        Mockito.when(fileImporter.importCrac("crac-url", network)).thenReturn(crac);

        Mockito.when(fileExporter.saveNetworkInArtifact(Mockito.any(), Mockito.any())).thenReturn("scaled-network-url");

        SweCsaRaoValidator sweCsaRaoValidator = new SweCsaRaoValidatorMock(fileExporter, raoRunnerClient);

        DichotomyRunner sweCsaDichotomyRunner = new DichotomyRunner(sweCsaRaoValidator, fileImporter, fileExporter);
        CsaRequest csaRequest = new CsaRequest("id", "2023-09-13T09:30:00Z", "cgm-url", "crac-url", "rao-result-url");
        assertNotNull(sweCsaDichotomyRunner.runDichotomy(csaRequest));
    }


    public class SweCsaRaoValidatorMock extends SweCsaRaoValidator {
        Set<FlowCnec> criticalCnecs = new HashSet<>();
        FileExporter fileExporter;
        AsynchronousRaoRunnerClient raoRunnerClient;

        public SweCsaRaoValidatorMock(FileExporter fileExporter, AsynchronousRaoRunnerClient raoRunnerClient) {
            super(fileExporter,
                raoRunnerClient);
            this.fileExporter = fileExporter;
            this.raoRunnerClient = raoRunnerClient;
        }

        @Override
        public DichotomyStepResult validateNetwork(String stepFolder, Network network, Crac crac, CsaRequest csaRequest, String raoParametersUrl, boolean withVoltageMonitoring, boolean withAngleMonitoring) {
            RaoParameters raoParameters = new RaoParameters();
            JsonRaoParameters.update(raoParameters, getClass().getResourceAsStream("/RaoParameters.json"));
            Network networkCopy = NetworkSerDe.copy(network);
            RaoInput raoInput = RaoInput.build(networkCopy, crac).build();
           // RaoResult raoResult = Rao.find("SearchTreeRao").run(raoInput, raoParameters, null);
            RaoResult raoResult = FastRao.launchFilteredRao(raoInput, raoParameters, null, criticalCnecs);

            RaoResponse raoResponse = Mockito.mock(RaoResponse.class);
            Set<FlowCnec> frEsFlowCnecs = crac.getFlowCnecs().stream()
                .filter(flowCnec -> flowCnec.isOptimized() && flowCnec.getLocation(network).contains(Optional.of(Country.FR)))
                .collect(Collectors.toSet());
            Set<FlowCnec> ptEsFlowCnecs = crac.getFlowCnecs().stream()
                .filter(flowCnec -> flowCnec.isOptimized() && flowCnec.getLocation(network).contains(Optional.of(Country.PT)))
                .collect(Collectors.toSet());
            boolean cnecsOnPtEsBorderAreSecure = hasNoFlowCnecNegativeMargin(raoResult, ptEsFlowCnecs);
            boolean cnecsOnFrEsBorderAreSecure = hasNoFlowCnecNegativeMargin(raoResult, frEsFlowCnecs);
            return DichotomyStepResult.fromNetworkValidationResult(raoResult, raoResponse, cnecsOnPtEsBorderAreSecure, cnecsOnFrEsBorderAreSecure);
        }
    }
}
