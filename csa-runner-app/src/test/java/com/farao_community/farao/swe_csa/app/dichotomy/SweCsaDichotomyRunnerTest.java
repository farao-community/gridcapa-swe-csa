package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.farao_community.farao.swe_csa.app.FileTestUtils;
import com.powsybl.openrao.data.cracapi.Crac;
import com.farao_community.farao.swe_csa.app.dichotomy.dispatcher.SweCsaShiftDispatcher;
import com.farao_community.farao.swe_csa.app.dichotomy.index.SweCsaHalfRangeDivisionIndexStrategy;
import com.farao_community.farao.swe_csa.app.dichotomy.shifter.SweCsaNetworkShifter;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import com.powsybl.openrao.raoapi.Rao;
import com.powsybl.openrao.raoapi.RaoInput;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
public class SweCsaDichotomyRunnerTest {

    @Mock
    RaoRunnerClient raoRunnerClient;
    @Autowired
    FileImporter fileImporter;
    @Autowired
    FileTestUtils fileTestUtils;

    /*RaoResponse testGetDichotomyResponseWithCoresoTest(String zipUrl, String timeStamp) throws IOException {
        Path filePath = Paths.get(new File(getClass().getResource(zipUrl).getFile()).toString());
        Instant utcInstant = Instant.parse(timeStamp);
        Network network = fileTestUtils.getNetworkFromResource(filePath);
        Crac crac = fileTestUtils.importCrac(filePath, network, utcInstant);
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy = new SweCsaHalfRangeDivisionIndexStrategy(crac, network);
        SweCsaDichotomyRunner sweCsaDichotomyRunner = new SweCsaDichotomyRunner(raoRunnerClient, fileImporter);
        sweCsaDichotomyRunner.setIndexPrecision(10);

        String networkFileUrl = "networkFileUrl";
        String cracFileUrl = "cracFileUrl";
        String raoParametersUrl = "raoParametersUrl";
        Mockito.when(raoRunnerClient.runRao(any())).then(i -> {
            RaoRequest raoRequest = i.getArgument(0);
            //TODO : use this mock to redirect raoRunnerClient calls to run directly Rao
            // we must convert the raoRequest into raoInput
            // and convert the raoResult into raoResponse (so we should have raoResult into a file

            RaoInput.RaoInputBuilder raoInputBuilder = RaoInput.build(network, crac);
            RaoResult raoResult = Rao.find().run(raoInputBuilder.build(), new RaoParameters());
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            File fileRaoResult = Files.createTempFile("raoResult" + raoResult.toString(), ".tmp", attr).toFile();
            RaoResponse raoResponse = new RaoResponse.RaoResponseBuilder().withRaoResultFileUrl(fileRaoResult);
        });

        SweCsaRaoValidator validator = new SweCsaRaoValidator(raoRunnerClient, "requestId", networkFileUrl, cracFileUrl, crac, raoParametersUrl, sweCsaDichotomyRunner.getCnecsIdLists(indexStrategy));
        RaoResponse raoResponseAfterDichotomy = sweCsaDichotomyRunner.getDichotomyResponse(network, crac, validator, indexStrategy);
        return raoResponseAfterDichotomy;
    }

    @Test
    void launchCoresoTest() throws IOException {
        RaoResponse raoResponseAfterDichotomy = testGetDichotomyResponseWithCoresoTest("/TestCase_1_31_1.zip", "2024-01-12T09:30:00Z");

        assertNotNull(raoResponseAfterDichotomy);
    }*/

    @Test
    void testGetEngine() {
        Network network = fileImporter.importNetwork(Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        Crac crac = fileImporter.importCrac(Objects.requireNonNull(getClass().getResource("/rao_inputs/crac.json")).toString());
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy = new SweCsaHalfRangeDivisionIndexStrategy(crac, network);
        SweCsaRaoValidator validatorMock = Mockito.mock(SweCsaRaoValidator.class);
        SweCsaDichotomyRunner sweCsaDichotomyRunner = new SweCsaDichotomyRunner(raoRunnerClient, fileImporter);
        sweCsaDichotomyRunner.setIndexPrecision(10);
        SweCsaDichotomyEngine dichotomyEngine = sweCsaDichotomyRunner.getEngine(network, crac, validatorMock, indexStrategy);

        assertNotNull(dichotomyEngine);

        assertEquals(Country.ES, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.ES_PT.getName()).getExportingCountry());
        assertEquals(Country.PT, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.ES_PT.getName()).getImportingCountry());
        assertEquals(Country.PT, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.PT_ES.getName()).getExportingCountry());
        assertEquals(Country.ES, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.PT_ES.getName()).getImportingCountry());
        assertEquals(Country.ES, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.ES_FR.getName()).getExportingCountry());
        assertEquals(Country.FR, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.ES_FR.getName()).getImportingCountry());
        assertEquals(Country.FR, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.FR_ES.getName()).getExportingCountry());
        assertEquals(Country.ES, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.FR_ES.getName()).getImportingCountry());

        assertEquals("CT_FRES : -0, CT_PTES : -0", dichotomyEngine.getIndex().maxValue().print());
        assertEquals("CT_FRES : 0, CT_PTES : 0", dichotomyEngine.getIndex().minValue().print());

        assertEquals(9, ((SweCsaHalfRangeDivisionIndexStrategy) dichotomyEngine.getIndexStrategy()).getFrEsFlowCnecs().size());
        assertEquals(0, ((SweCsaHalfRangeDivisionIndexStrategy) dichotomyEngine.getIndexStrategy()).getPtEsFlowCnecs().size());
        assertEquals(0, ((SweCsaHalfRangeDivisionIndexStrategy) dichotomyEngine.getIndexStrategy()).getFrEsAngleCnecs().size());
        assertEquals(0, ((SweCsaHalfRangeDivisionIndexStrategy) dichotomyEngine.getIndexStrategy()).getPtEsAngleCnecs().size());

        SweCsaNetworkShifter sweCsaNetworkShifter = (SweCsaNetworkShifter) dichotomyEngine.getNetworkShifter();
        SweCsaShiftDispatcher shiftDispatcher = (SweCsaShiftDispatcher) sweCsaNetworkShifter.getShiftDispatcher();

        assertEquals(-0.0, shiftDispatcher.getInitialNetPositions().get(Country.FR.getName()));
        assertEquals(-0.0, shiftDispatcher.getInitialNetPositions().get(Country.PT.getName()));
        assertEquals(0.0, shiftDispatcher.getInitialNetPositions().get(Country.ES.getName()));
    }
}
