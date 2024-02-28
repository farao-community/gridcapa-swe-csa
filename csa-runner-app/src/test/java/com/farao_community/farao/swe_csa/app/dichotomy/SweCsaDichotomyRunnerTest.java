package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.swe_csa.app.FileHelper;
import com.farao_community.farao.swe_csa.app.dichotomy.dispatcher.SweCsaShiftDispatcher;
import com.farao_community.farao.swe_csa.app.dichotomy.index.SweCsaHalfRangeDivisionIndexStrategy;
import com.farao_community.farao.swe_csa.app.dichotomy.shifter.SweCsaNetworkShifter;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class SweCsaDichotomyRunnerTest {

    @Autowired
    FileHelper fileHelper;
    @Autowired
    SweCsaDichotomyRunner sweCsaDichotomyRunner;

    /*RaoResponse runWithFileAndTimestamp(String fileName, String timestamp) throws IOException {
        Path filePath = Paths.get(new File(getClass().getResource(fileName).getFile()).toString());
        Network network = fileHelper.importNetwork(Paths.get(new File(getClass().getResource(fileName).getFile()).toString()));
        Crac crac = fileHelper.importCrac(filePath, network, Instant.parse(timestamp));
        String networkFileUrl = fileHelper.uploadIidmNetworkToMinio("requestId", network, Instant.parse(timestamp));
        String cracFileUrl = fileHelper.uploadJsonCrac("requestId", crac, Instant.parse(timestamp));
        String raoParametersUrl = fileHelper.uploadRaoParameters("requestId", Instant.parse(timestamp));
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy = new SweCsaHalfRangeDivisionIndexStrategy(crac, network);
        Pair<List<String>, List<String>> cnecsIds = Pair.of(indexStrategy.getFrEsCnecs().stream().map(Cnec::getId).collect(Collectors.toList()),
            indexStrategy.getPtEsCnecs().stream().map(Cnec::getId).collect(Collectors.toList()));
        SweCsaRaoValidator validator = new SweCsaRaoValidator(this.raoRunnerClient, "requestId", networkFileUrl,
            cracFileUrl, crac, raoParametersUrl, cnecsIds);
        RaoResponse raoResponseAfterDichotomy = sweCsaDichotomyRunner.getDichotomyResponse(network, crac, validator, indexStrategy);
        return raoResponseAfterDichotomy;
    }*/

    @Test
    void testGetEngine() {
        Path filePath = Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString());
        Network network = fileHelper.importNetwork(Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString()));
        Crac crac = fileHelper.importCrac(filePath, network, Instant.parse("2023-08-08T15:30:00Z"));
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy = new SweCsaHalfRangeDivisionIndexStrategy(crac, network);
        SweCsaRaoValidator validatorMock = Mockito.mock(SweCsaRaoValidator.class);
        SweCsaDichotomyEngine dichotomyEngine = sweCsaDichotomyRunner.getEngine(network, crac, validatorMock, indexStrategy);

        assertNotNull(dichotomyEngine);

        assertEquals(Country.ES, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.ES_PT.getName()).getExportingCountry());
        assertEquals(Country.PT, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.ES_PT.getName()).getImportingCountry());
        assertEquals(-500.0, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.ES_PT.getName()).getInitialSetpoint());
        assertEquals(Country.PT, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.PT_ES.getName()).getExportingCountry());
        assertEquals(Country.ES, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.PT_ES.getName()).getImportingCountry());
        assertEquals(500.0, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.PT_ES.getName()).getInitialSetpoint());
        assertEquals(Country.ES, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.ES_FR.getName()).getExportingCountry());
        assertEquals(Country.FR, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.ES_FR.getName()).getImportingCountry());
        assertEquals(600.0, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.ES_FR.getName()).getInitialSetpoint());
        assertEquals(Country.FR, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.FR_ES.getName()).getExportingCountry());
        assertEquals(Country.ES, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.FR_ES.getName()).getImportingCountry());
        assertEquals(-600.0, crac.getCounterTradeRangeAction(CounterTradeRangeActionDirection.FR_ES.getName()).getInitialSetpoint());

        assertEquals("CT_FRES : 600, CT_PTES : 500", dichotomyEngine.getIndex().maxValue().print());
        assertEquals("CT_FRES : 0, CT_PTES : 0", dichotomyEngine.getIndex().minValue().print());

        assertEquals(5, ((SweCsaHalfRangeDivisionIndexStrategy) dichotomyEngine.getIndexStrategy()).getFrEsFlowCnecs().size());
        assertEquals(0, ((SweCsaHalfRangeDivisionIndexStrategy) dichotomyEngine.getIndexStrategy()).getPtEsFlowCnecs().size());
        assertEquals(0, ((SweCsaHalfRangeDivisionIndexStrategy) dichotomyEngine.getIndexStrategy()).getFrEsAngleCnecs().size());
        assertEquals(0, ((SweCsaHalfRangeDivisionIndexStrategy) dichotomyEngine.getIndexStrategy()).getPtEsAngleCnecs().size());

        SweCsaNetworkShifter sweCsaNetworkShifter = (SweCsaNetworkShifter) dichotomyEngine.getNetworkShifter();
        SweCsaShiftDispatcher shiftDispatcher = (SweCsaShiftDispatcher) sweCsaNetworkShifter.getShiftDispatcher();

        assertEquals(600.0, shiftDispatcher.getInitialNetPositions().get(Country.FR.getName()));
        assertEquals(-500.0, shiftDispatcher.getInitialNetPositions().get(Country.PT.getName()));
        assertEquals(-100.0, shiftDispatcher.getInitialNetPositions().get(Country.ES.getName()));
    }
}
