package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.AsynchronousRaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.CracFactory;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class SweCsaDichotomyRunnerTest {

    @Mock
    FileImporter fileImporter;
    @Mock
    FileExporter fileExporter;
    @Mock
    AsynchronousRaoRunnerClient raoRunnerClient;

    @Test
    void runCounterTradingTest() throws GlskLimitationException, ShiftingException {
        Instant utcInstant = Instant.parse("2023-09-13T09:30:00Z");
        Network network = Network.read("/dichotomy/TestCase_with_swe_countries.xiidm", getClass().getResourceAsStream("/dichotomy/TestCase_with_swe_countries.xiidm"));
        Crac crac = CracFactory.findDefault().create("id");
        Mockito.when(fileImporter.uploadRaoParameters(utcInstant)).thenReturn("rao-parameters-url");
        Mockito.when(fileImporter.importNetwork("cgm-url")).thenReturn(network);
        Mockito.when(fileImporter.importCrac("crac-url", network)).thenReturn(crac);
        Mockito.when(fileExporter.saveNetworkInArtifact(Mockito.any(), Mockito.any())).thenReturn("scaled-network-url");
        RaoResponse raoResponse = Mockito.mock(RaoResponse.class);
        Mockito.when(raoRunnerClient.runRaoAsynchronously(Mockito.any())).thenReturn(CompletableFuture.completedFuture(raoResponse));
        SweCsaRaoValidator sweCsaRaoValidator = new SweCsaRaoValidatorMock(fileExporter, raoRunnerClient);

        DichotomyRunner sweCsaDichotomyRunner = new DichotomyRunner(sweCsaRaoValidator, fileImporter, fileExporter);
        sweCsaDichotomyRunner.setIndexPrecision(50);
        sweCsaDichotomyRunner.setMaxDichotomiesByBorder(10);
        CsaRequest csaRequest = new CsaRequest("id", "2023-09-13T09:30:00Z", "cgm-url", "crac-url", "rao-result-url");
        RaoResultWithCounterTradeRangeActions raoResult = (RaoResultWithCounterTradeRangeActions) sweCsaDichotomyRunner.runDichotomy(csaRequest);
        Iterator<CounterTradeRangeActionResult> ctRaResultIt  = raoResult.getCounterTradingResult().getCounterTradeRangeActionResults().values().stream().sorted(Comparator.comparing(CounterTradeRangeActionResult::getCtRangeActionId)).collect(Collectors.toCollection(LinkedHashSet::new)).iterator();
        CounterTradeRangeActionResult frEsCtRaResult = ctRaResultIt.next();
        CounterTradeRangeActionResult ptEsCtRaResult = ctRaResultIt.next();

        assertEquals("CT_RA_PTES", ptEsCtRaResult.getCtRangeActionId());
        assertEquals(0., ptEsCtRaResult.getSetPoint());
        assertEquals("CT_RA_FRES", frEsCtRaResult.getCtRangeActionId());
        assertEquals(629., frEsCtRaResult.getSetPoint(), 1);
    }

    public static class SweCsaRaoValidatorMock extends SweCsaRaoValidator {
        FileExporter fileExporter;
        AsynchronousRaoRunnerClient raoRunnerClient;

        public SweCsaRaoValidatorMock(FileExporter fileExporter, AsynchronousRaoRunnerClient raoRunnerClient) {
            super(fileExporter,
                raoRunnerClient);
            this.fileExporter = fileExporter;
            this.raoRunnerClient = raoRunnerClient;
        }

        @Override
        public DichotomyStepResult validateNetwork(String stepFolder, Network network, Crac crac, CsaRequest csaRequest, String raoParametersUrl, boolean withVoltageMonitoring, boolean withAngleMonitoring, CounterTradingValues counterTradingValues) {
            RaoResponse raoResponse = Mockito.mock(RaoResponse.class);
            RaoResult raoResult = Mockito.mock(RaoResult.class);
            boolean cnecsOnPtEsBorderAreSecure = counterTradingValues.getPtEsCt() >= 0 && counterTradingValues.getPtEsCt() < 50;
            boolean cnecsOnFrEsBorderAreSecure = counterTradingValues.getFrEsCt() >= 600 && counterTradingValues.getFrEsCt() <= 2012;
            return DichotomyStepResult.fromNetworkValidationResult(raoResult, raoResponse, cnecsOnPtEsBorderAreSecure, cnecsOnFrEsBorderAreSecure, counterTradingValues);
        }
    }
}

