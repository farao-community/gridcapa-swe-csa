package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.app.FileImporter;
import com.powsybl.openrao.data.cracapi.Crac;
import com.farao_community.farao.dichotomy.api.exceptions.DichotomyException;
import com.farao_community.farao.dichotomy.api.exceptions.ValidationException;
import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.dichotomy.api.results.LimitingCause;
import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.swe_csa.app.dichotomy.dispatcher.SweCsaShiftDispatcher;
import com.farao_community.farao.swe_csa.app.dichotomy.index.Index;
import com.farao_community.farao.swe_csa.app.dichotomy.index.SweCsaHalfRangeDivisionIndexStrategy;
import com.farao_community.farao.swe_csa.app.dichotomy.shifter.SweCsaNetworkShifter;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
public class SweCsaDichotomyEngineTest {

    @Autowired
    FileImporter fileImporter;

    @Test
    void newSweCsaDichotomyEngineTestWithoutEnoughIterations() {
        Network network = fileImporter.importNetwork(Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        Crac crac = fileImporter.importCrac(Objects.requireNonNull(getClass().getResource("/rao_inputs/crac.json")).toString(), network);
        assertThrows(DichotomyException.class, () -> new SweCsaDichotomyEngine(
            new Index<>(new MultipleDichotomyVariables(new HashMap<>()), new MultipleDichotomyVariables(new HashMap<>()), 10),
            new SweCsaHalfRangeDivisionIndexStrategy(crac, network),
            new SweCsaNetworkShifter(SweCsaZonalData.getZonalData(network), new SweCsaShiftDispatcher(new HashMap<>())),
            Mockito.mock(SweCsaRaoValidator.class), 2));
    }

    @Test
    void runTest() throws ValidationException {
        Network network = fileImporter.importNetwork(Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        SweCsaHalfRangeDivisionIndexStrategy indexStrategyMock = Mockito.mock(SweCsaHalfRangeDivisionIndexStrategy.class);
        Mockito.when(indexStrategyMock.nextValue(any()))
            .thenReturn(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1250.0, CounterTradingDirection.FR_ES.getName(), 550.0)));
        SweCsaNetworkShifter sweCsaNetworkShifterMock = Mockito.mock(SweCsaNetworkShifter.class);
        SweCsaRaoValidator sweCsaRaoValidatorMock = Mockito.mock(SweCsaRaoValidator.class);
        DichotomyStepResult<RaoResponse> dichotomyStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.UNSECURE_AFTER_VALIDATION, "test");
        Mockito.when(sweCsaRaoValidatorMock.validateNetwork(any(), any())).thenReturn(dichotomyStepResult);
        SweCsaDichotomyEngine dichotomyEngine = new SweCsaDichotomyEngine(
            new Index<>(new MultipleDichotomyVariables(new HashMap<>()), new MultipleDichotomyVariables(new HashMap<>()), 10),
            indexStrategyMock, sweCsaNetworkShifterMock, sweCsaRaoValidatorMock, 3);
        DichotomyResult<RaoResponse, MultipleDichotomyVariables> dichotomyResult = dichotomyEngine.run(network);

        assertNotNull(dichotomyResult);
        assertNotNull(dichotomyResult.getHighestInvalidStep());
        assertEquals(LimitingCause.INDEX_EVALUATION_OR_MAX_ITERATION.name(), dichotomyResult.getLimitingCause().name());
    }
}
