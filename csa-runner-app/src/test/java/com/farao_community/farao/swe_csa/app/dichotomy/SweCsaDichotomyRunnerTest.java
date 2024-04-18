package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.api.results.CounterTradingResult;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.farao_community.farao.swe_csa.app.FileTestUtils;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.iidm.serde.NetworkSerDe;
import com.powsybl.openrao.data.cracapi.Crac;
import com.farao_community.farao.swe_csa.app.dichotomy.dispatcher.SweCsaShiftDispatcher;
import com.farao_community.farao.swe_csa.app.dichotomy.index.SweCsaHalfRangeDivisionIndexStrategy;
import com.farao_community.farao.swe_csa.app.dichotomy.shifter.SweCsaNetworkShifter;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.cnec.FlowCnec;
import com.powsybl.openrao.data.cracapi.networkaction.NetworkAction;
import com.powsybl.openrao.data.cracapi.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import com.powsybl.openrao.raoapi.Rao;
import com.powsybl.openrao.raoapi.RaoInput;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.searchtreerao.faorao.FastRao;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.*;

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

    RaoResponse testGetDichotomyResponseWithCoresoTest(String zipUrl, String timeStamp) throws IOException {
        Logger logger = LoggerFactory.getLogger(this.getClass());

        Path filePath = Paths.get(new File(getClass().getResource(zipUrl).getFile()).toString());
        Instant utcInstant = Instant.parse(timeStamp);
        Network network = fileTestUtils.getNetworkFromResource(filePath);
        Crac crac = fileTestUtils.importCrac(filePath, network, utcInstant);
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy = new SweCsaHalfRangeDivisionIndexStrategy(crac, network);
        SweCsaDichotomyRunner sweCsaDichotomyRunner = new SweCsaDichotomyRunner(raoRunnerClient, fileImporter);
        sweCsaDichotomyRunner.setIndexPrecision(10);

        RaoParameters raoParameters = new RaoParameters();
        JsonRaoParameters.update(raoParameters, getClass().getResourceAsStream("/RaoParameters.json"));
        SweCsaRaoValidator validator = new SweCsaRaoValidatorMock(crac, raoParameters, sweCsaDichotomyRunner.getCnecsIdLists(indexStrategy));
        //(raoRunnerClient, "requestId", networkFileUrl, cracFileUrl, crac, raoParametersUrl, sweCsaDichotomyRunner.getCnecsIdLists(indexStrategy));
        RaoResponse raoResponseAfterDichotomy = sweCsaDichotomyRunner.getDichotomyResponse(network, crac, validator, indexStrategy);
        return raoResponseAfterDichotomy;
    }

    @Test
    void launchCoresoTest() throws IOException {
        RaoResponse raoResponseAfterDichotomy = testGetDichotomyResponseWithCoresoTest("/TestCase_1_29.zip", "2023-09-13T09:30:00Z");

        assertNotNull(raoResponseAfterDichotomy);
    }

    @Test
    void testGetEngine() {
        Network network = fileImporter.importNetwork(Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        Crac crac = fileImporter.importCrac(Objects.requireNonNull(getClass().getResource("/rao_inputs/crac.json")).toString(), network);
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

    public class SweCsaRaoValidatorMock extends SweCsaRaoValidator {
        Crac crac;
        RaoParameters raoParameters;
        Set<FlowCnec> criticalCnecs = new HashSet<>();
        private MultipleDichotomyVariables counterTradingValue;
        private List<String> frEsCnecs;
        private List<String> ptEsCnecs;


        public SweCsaRaoValidatorMock(Crac crac, RaoParameters raoParameters, Pair<List<String>, List<String>> cnecs) {
            super(null,
                null,
                null,
                null,
                crac,
                null,
                cnecs);
            this.crac = crac;
            this.raoParameters = raoParameters;
            this.frEsCnecs = cnecs.getLeft();
            this.ptEsCnecs = cnecs.getRight();
        }

        @Override
        public DichotomyStepResult<RaoResponse> validateNetwork(Network network, DichotomyStepResult<RaoResponse> lastDichotomyStepResult) {

            Network networkCopy = NetworkSerDe.copy(network);
            RaoInput raoInput = RaoInput.build(networkCopy, crac).build();
            //RaoResult raoResult = Rao.find("SearchTreeRao").run(raoInput, raoParameters, null);
            RaoResult raoResult = FastRao.launchFilteredRao(raoInput, raoParameters, null, criticalCnecs);
            RaoResultWithCounterTradeRangeActions raoResultWithCounterTradeRangeActions = this.createRaoResultWithCtRa(raoResult);

            RaoResponse raoResponse = Mockito.mock(RaoResponse.class);

            return DichotomyStepResult.fromNetworkValidationResult(this.createRaoResultWithCtRa(raoResultWithCounterTradeRangeActions), raoResponse);
        }

        private RaoResultWithCounterTradeRangeActions createRaoResultWithCtRa(RaoResult raoResult) {
            CounterTradeRangeActionResult counterTradeRangeActionResultFrEs = new CounterTradeRangeActionResult(CounterTradingDirection.FR_ES.getName(),
                this.counterTradingValue.values().get(CounterTradingDirection.FR_ES.getName()), frEsCnecs);
            CounterTradeRangeActionResult counterTradeRangeActionResultPtEs = new CounterTradeRangeActionResult(CounterTradingDirection.PT_ES.getName(),
                this.counterTradingValue.values().get(CounterTradingDirection.PT_ES.getName()), ptEsCnecs);
            Map<CounterTradeRangeAction, CounterTradeRangeActionResult> counterTradeRangeActionResults = Map.of(
                crac.getCounterTradeRangeAction("CT_RA_FRES"), counterTradeRangeActionResultFrEs,
                crac.getCounterTradeRangeAction("CT_RA_PTES"), counterTradeRangeActionResultPtEs
            );
            CounterTradingResult counterTradingResult = new CounterTradingResult(counterTradeRangeActionResults);
            return new RaoResultWithCounterTradeRangeActions(raoResult, counterTradingResult);
        }

        public void setCounterTradingValue(MultipleDichotomyVariables counterTradingValue) {
            this.counterTradingValue = counterTradingValue;
        }
    }
}
