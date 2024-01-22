package com.farao_community.farao.swe_csa.app.rao_result;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.range_action.CounterTradeRangeAction;
import com.farao_community.farao.data.crac_io_json.JsonImport;
import com.farao_community.farao.data.rao_result_api.ComputationStatus;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.rao_result_json.RaoResultImporter;
import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.api.results.CounterTradingResult;
import com.powsybl.iidm.network.Country;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RaoResultWithCounterTradeRangeActionsTest {

    private CounterTradingResult counterTradingResult;
    State preventiveState = Mockito.mock(State.class);
    Map<CounterTradeRangeAction, CounterTradeRangeActionResult> ctRasToResultsMap = new HashMap<>();
    CounterTradeRangeAction frEsCounterTradeRangeActionMock = Mockito.mock(CounterTradeRangeAction.class);
    CounterTradeRangeAction esFrCounterTradeRangeActionMock = Mockito.mock(CounterTradeRangeAction.class);
    CounterTradeRangeAction esPtCounterTradeRangeActionMock = Mockito.mock(CounterTradeRangeAction.class);
    CounterTradeRangeAction ptEsCounterTradeRangeActionMock = Mockito.mock(CounterTradeRangeAction.class);

    @BeforeEach
    public void setUp() {
        Mockito.when(preventiveState.isPreventive()).thenReturn(true);

        Mockito.when(frEsCounterTradeRangeActionMock.getGroupId()).thenReturn(Optional.of("CT_FR_ES"));
        Mockito.when(frEsCounterTradeRangeActionMock.getImportingCountry()).thenReturn(Country.ES);
        Mockito.when(frEsCounterTradeRangeActionMock.getExportingCountry()).thenReturn(Country.FR);
        Mockito.when(frEsCounterTradeRangeActionMock.getInitialSetpoint()).thenReturn(0.);

        Mockito.when(esFrCounterTradeRangeActionMock.getGroupId()).thenReturn(Optional.of("CT_ES_FR"));
        Mockito.when(esFrCounterTradeRangeActionMock.getImportingCountry()).thenReturn(Country.FR);
        Mockito.when(esFrCounterTradeRangeActionMock.getExportingCountry()).thenReturn(Country.ES);
        Mockito.when(esFrCounterTradeRangeActionMock.getInitialSetpoint()).thenReturn(0.);

        Mockito.when(esPtCounterTradeRangeActionMock.getGroupId()).thenReturn(Optional.of("CT_ES_PT"));
        Mockito.when(esPtCounterTradeRangeActionMock.getImportingCountry()).thenReturn(Country.PT);
        Mockito.when(esPtCounterTradeRangeActionMock.getExportingCountry()).thenReturn(Country.ES);
        Mockito.when(esPtCounterTradeRangeActionMock.getInitialSetpoint()).thenReturn(0.);

        Mockito.when(ptEsCounterTradeRangeActionMock.getGroupId()).thenReturn(Optional.of("CT_PT_ES"));
        Mockito.when(ptEsCounterTradeRangeActionMock.getImportingCountry()).thenReturn(Country.FR);
        Mockito.when(ptEsCounterTradeRangeActionMock.getExportingCountry()).thenReturn(Country.ES);
        Mockito.when(ptEsCounterTradeRangeActionMock.getInitialSetpoint()).thenReturn(0.);

        CounterTradeRangeActionResult frEsCounterTradeRangeActionResult = new CounterTradeRangeActionResult("CT_FR_ES", 10.0, Arrays.asList("CNEC1", "CNEC2"));
        CounterTradeRangeActionResult esFrCounterTradeRangeActionResult = new CounterTradeRangeActionResult("CT_ES_FR", -10.0, Arrays.asList("CNEC1", "CNEC2"));

        CounterTradeRangeActionResult esPtCounterTradeRangeActionResult = new CounterTradeRangeActionResult("CT_ES_PT", 0., Arrays.asList("CNEC3", "CNEC4"));
        CounterTradeRangeActionResult ptEsCounterTradeRangeActionResult = new CounterTradeRangeActionResult("CT_ES_FR", 0., Arrays.asList("CNEC3", "CNEC4"));

        ctRasToResultsMap.put(frEsCounterTradeRangeActionMock, frEsCounterTradeRangeActionResult);
        ctRasToResultsMap.put(esFrCounterTradeRangeActionMock, esFrCounterTradeRangeActionResult);
        ctRasToResultsMap.put(esPtCounterTradeRangeActionMock, esPtCounterTradeRangeActionResult);
        ctRasToResultsMap.put(ptEsCounterTradeRangeActionMock, ptEsCounterTradeRangeActionResult);

        counterTradingResult = new CounterTradingResult(ctRasToResultsMap);
    }

    @Test
    void testRaoResultWithCounterTrading() {
        InputStream raoResultFile = getClass().getResourceAsStream("/rao_result/rao-result-v1.4.json");
        InputStream cracFile = getClass().getResourceAsStream("/rao_result/crac-for-rao-result-v1.4.json");
        Crac crac = new JsonImport().importCrac(cracFile);
        RaoResult raoResult = new RaoResultImporter().importRaoResult(raoResultFile, crac);

        RaoResult raoResultWithCounterTrading = new RaoResultWithCounterTradeRangeActions(raoResult, counterTradingResult);

        assertEquals(ComputationStatus.DEFAULT, raoResultWithCounterTrading.getComputationStatus());
        assertTrue(counterTradingResult.isActivatedDuringState(preventiveState, frEsCounterTradeRangeActionMock));
        assertTrue(counterTradingResult.isActivatedDuringState(preventiveState, esFrCounterTradeRangeActionMock));
        assertFalse(counterTradingResult.isActivatedDuringState(preventiveState, ptEsCounterTradeRangeActionMock));
        assertFalse(counterTradingResult.isActivatedDuringState(preventiveState, esPtCounterTradeRangeActionMock));

        assertEquals(0., counterTradingResult.getPreOptimizationSetPointOnState(preventiveState, frEsCounterTradeRangeActionMock));
        assertEquals(0., counterTradingResult.getPreOptimizationSetPointOnState(preventiveState, esFrCounterTradeRangeActionMock));
        assertEquals(0., counterTradingResult.getPreOptimizationSetPointOnState(preventiveState, ptEsCounterTradeRangeActionMock));
        assertEquals(0., counterTradingResult.getPreOptimizationSetPointOnState(preventiveState, esPtCounterTradeRangeActionMock));

        assertEquals(10., counterTradingResult.getOptimizedSetPointOnState(preventiveState, frEsCounterTradeRangeActionMock));
        assertEquals(-10., counterTradingResult.getOptimizedSetPointOnState(preventiveState, esFrCounterTradeRangeActionMock));
        assertEquals(0., counterTradingResult.getOptimizedSetPointOnState(preventiveState, ptEsCounterTradeRangeActionMock));
        assertEquals(0., counterTradingResult.getOptimizedSetPointOnState(preventiveState, esPtCounterTradeRangeActionMock));

        assertEquals(Set.of(esFrCounterTradeRangeActionMock, frEsCounterTradeRangeActionMock), counterTradingResult.getActivatedRangeActionsDuringState(preventiveState));
    }

}
