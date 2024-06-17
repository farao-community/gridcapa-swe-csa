package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.CracFactory;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SweCsaDichotomyRunnerTest {

    @Mock
    FileImporter fileImporter;
    @Mock
    FileExporter fileExporter;
    @Mock
    RaoRunnerClient raoRunnerClient;

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
        Mockito.when(raoRunnerClient.runRao(Mockito.any())).thenReturn(raoResponse);
        SweCsaRaoValidator sweCsaRaoValidator = new SweCsaRaoValidatorMock(fileExporter, raoRunnerClient);

        DichotomyRunner sweCsaDichotomyRunner = new DichotomyRunner(sweCsaRaoValidator, fileImporter, fileExporter);
        sweCsaDichotomyRunner.setIndexPrecision(50);
        sweCsaDichotomyRunner.setMaxDichotomiesByBorder(10);
        CsaRequest csaRequest = new CsaRequest("id", "2023-09-13T09:30:00Z", "cgm-url", "crac-url", "rao-result-url");
        RaoResultWithCounterTradeRangeActions raoResult = (RaoResultWithCounterTradeRangeActions) sweCsaDichotomyRunner.runDichotomy(csaRequest);

        Iterator<CounterTradeRangeActionResult> ctRaResultIt  = raoResult.getCounterTradingResult().getCounterTradeRangeActionResults().values().stream().sorted(Comparator.comparing(CounterTradeRangeActionResult::getCtRangeActionId)).collect(Collectors.toCollection(LinkedHashSet::new)).iterator();

        CounterTradeRangeActionResult esFrCtRaResult = ctRaResultIt.next();
        assertEquals("CT_RA_ESFR", esFrCtRaResult.getCtRangeActionId());
        assertEquals(629., esFrCtRaResult.getSetPoint(), 1);

        CounterTradeRangeActionResult esPtCtRaResult = ctRaResultIt.next();
        assertEquals("CT_RA_ESPT", esPtCtRaResult.getCtRangeActionId());
        assertEquals(0., esPtCtRaResult.getSetPoint());

        CounterTradeRangeActionResult frEsCtRaResult = ctRaResultIt.next();
        assertEquals("CT_RA_FRES", frEsCtRaResult.getCtRangeActionId());
        assertEquals(629., frEsCtRaResult.getSetPoint(), 1);

        CounterTradeRangeActionResult ptEsCtRaResult = ctRaResultIt.next();
        assertEquals("CT_RA_PTES", ptEsCtRaResult.getCtRangeActionId());
        assertEquals(0., ptEsCtRaResult.getSetPoint());
    }


}

