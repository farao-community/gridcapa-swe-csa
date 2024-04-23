package com.farao_community.farao.swe_csa.app.rao_result;

import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.RemedialAction;
import com.powsybl.openrao.data.cracapi.State;
import com.powsybl.openrao.data.cracapi.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.cracapi.rangeaction.RangeAction;
import com.powsybl.openrao.data.craciojson.JsonImport;
import com.powsybl.openrao.data.raoresultapi.ComputationStatus;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import com.powsybl.openrao.data.raoresultjson.RaoResultImporter;
import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.api.results.CounterTradingResult;
import com.powsybl.iidm.network.Country;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;

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

    // TODO fixme related to farao upgrade , network required to import crac
   /* @Test
    void testRaoResultWithCounterTrading() {
        InputStream raoResultFile = getClass().getResourceAsStream("/rao_result/rao-result-v1.4.json");
        InputStream cracFile = getClass().getResourceAsStream("/rao_result/crac-for-rao-result-v1.4.json");
        Crac crac = new JsonImport().importCrac(cracFile, Mockito.mock(Network.class));
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
*/
    @Test
    void testIsActivatedDuringStateRemedialAction() {
        CounterTradingResult counterTradingResultMock = Mockito.mock(CounterTradingResult.class);
        RaoResult raoResultMock = Mockito.mock(RaoResult.class);
        RaoResultWithCounterTradeRangeActions raoResultWithCounterTradeRangeActions = new RaoResultWithCounterTradeRangeActions(raoResultMock, counterTradingResultMock);
        State stateMock = Mockito.mock(State.class);
        RemedialAction<?> remedialActionMock = Mockito.mock(RemedialAction.class);
        Mockito.when(raoResultMock.isActivatedDuringState(any(), (RemedialAction<?>) any())).thenReturn(true);
        assertTrue(raoResultWithCounterTradeRangeActions.isActivatedDuringState(stateMock, remedialActionMock));

        Mockito.when(raoResultMock.isActivatedDuringState(any(), (RemedialAction<?>) any())).thenReturn(false);
        Mockito.when(counterTradingResultMock.isActivatedDuringState(any(), (RemedialAction<?>) any())).thenReturn(true);
        assertTrue(raoResultWithCounterTradeRangeActions.isActivatedDuringState(stateMock, remedialActionMock));

        Mockito.when(counterTradingResultMock.isActivatedDuringState(any(), (RemedialAction<?>) any())).thenReturn(false);
        assertFalse(raoResultWithCounterTradeRangeActions.isActivatedDuringState(stateMock, remedialActionMock));
    }

    @Test
    void testIsActivatedDuringStateRangeAction() {
        CounterTradingResult counterTradingResultMock = Mockito.mock(CounterTradingResult.class);
        RaoResult raoResultMock = Mockito.mock(RaoResult.class);
        RaoResultWithCounterTradeRangeActions raoResultWithCounterTradeRangeActions = new RaoResultWithCounterTradeRangeActions(raoResultMock, counterTradingResultMock);
        State stateMock = Mockito.mock(State.class);
        RangeAction<?> rangeActionMock = Mockito.mock(RangeAction.class);
        Mockito.when(raoResultMock.isActivatedDuringState(any(), (RangeAction<?>) any())).thenReturn(true);
        assertTrue(raoResultWithCounterTradeRangeActions.isActivatedDuringState(stateMock, rangeActionMock));

        Mockito.when(raoResultMock.isActivatedDuringState(any(), (RangeAction<?>) any())).thenReturn(false);
        Mockito.when(counterTradingResultMock.isActivatedDuringState(any(), (RangeAction<?>) any())).thenReturn(true);
        assertTrue(raoResultWithCounterTradeRangeActions.isActivatedDuringState(stateMock, rangeActionMock));

        Mockito.when(counterTradingResultMock.isActivatedDuringState(any(), (RangeAction<?>) any())).thenReturn(false);
        assertFalse(raoResultWithCounterTradeRangeActions.isActivatedDuringState(stateMock, rangeActionMock));
    }

    @Test
    void testGetPreOptimizationSetPointOnState() {
        CounterTradingResult counterTradingResultMock = Mockito.mock(CounterTradingResult.class);
        RaoResult raoResultMock = Mockito.mock(RaoResult.class);
        RaoResultWithCounterTradeRangeActions raoResultWithCounterTradeRangeActions = new RaoResultWithCounterTradeRangeActions(raoResultMock, counterTradingResultMock);
        State stateMock = Mockito.mock(State.class);
        RangeAction<?> rangeActionMock = Mockito.mock(RangeAction.class);
        Mockito.when(raoResultMock.getPreOptimizationSetPointOnState(any(), any())).thenReturn(1.1);
        assertEquals(1.1, raoResultWithCounterTradeRangeActions.getPreOptimizationSetPointOnState(stateMock, rangeActionMock));

        CounterTradeRangeAction counterTradeRangeActionMock = Mockito.mock(CounterTradeRangeAction.class);
        Mockito.when(counterTradingResultMock.getPreOptimizationSetPointOnState(any(), any())).thenReturn(2.2);
        assertEquals(2.2, raoResultWithCounterTradeRangeActions.getPreOptimizationSetPointOnState(stateMock, counterTradeRangeActionMock));
    }

    @Test
    void testGetOptimizedSetPointOnState() {
        CounterTradingResult counterTradingResultMock = Mockito.mock(CounterTradingResult.class);
        RaoResult raoResultMock = Mockito.mock(RaoResult.class);
        RaoResultWithCounterTradeRangeActions raoResultWithCounterTradeRangeActions = new RaoResultWithCounterTradeRangeActions(raoResultMock, counterTradingResultMock);
        State stateMock = Mockito.mock(State.class);
        RangeAction<?> rangeActionMock = Mockito.mock(RangeAction.class);
        Mockito.when(raoResultMock.getOptimizedSetPointOnState(any(), any())).thenReturn(11.1);
        assertEquals(11.1, raoResultWithCounterTradeRangeActions.getOptimizedSetPointOnState(stateMock, rangeActionMock));

        CounterTradeRangeAction counterTradeRangeActionMock = Mockito.mock(CounterTradeRangeAction.class);
        Mockito.when(counterTradingResultMock.getOptimizedSetPointOnState(any(), any())).thenReturn(22.2);
        assertEquals(22.2, raoResultWithCounterTradeRangeActions.getOptimizedSetPointOnState(stateMock, counterTradeRangeActionMock));
    }

    @Test
    void testGetActivatedRangeActionsDuringState() {
        CounterTradingResult counterTradingResultMock = Mockito.mock(CounterTradingResult.class);
        RaoResult raoResultMock = Mockito.mock(RaoResult.class);
        RaoResultWithCounterTradeRangeActions raoResultWithCounterTradeRangeActions = new RaoResultWithCounterTradeRangeActions(raoResultMock, counterTradingResultMock);
        State stateMock1 = Mockito.mock(State.class);
        State stateMock2 = Mockito.mock(State.class);
        RangeAction<?> rangeActionMock1 = Mockito.mock(RangeAction.class);
        Mockito.when(rangeActionMock1.toString()).thenReturn("rangeActionMock1");
        RangeAction<?> rangeActionMock2 = Mockito.mock(RangeAction.class);
        Mockito.when(rangeActionMock2.toString()).thenReturn("rangeActionMock2");
        RangeAction<?> rangeActionMock3 = Mockito.mock(RangeAction.class);
        Mockito.when(rangeActionMock3.toString()).thenReturn("rangeActionMock3");
        RangeAction<?> rangeActionMock4 = Mockito.mock(RangeAction.class);
        Mockito.when(rangeActionMock4.toString()).thenReturn("rangeActionMock4");

        Mockito.when(counterTradingResultMock.getActivatedRangeActionsDuringState(stateMock1)).thenReturn(Set.of(rangeActionMock1));
        Mockito.when(counterTradingResultMock.getActivatedRangeActionsDuringState(stateMock2)).thenReturn(Set.of(rangeActionMock2));
        Mockito.when(raoResultMock.getActivatedRangeActionsDuringState(stateMock1)).thenReturn(Set.of(rangeActionMock3));
        Mockito.when(raoResultMock.getActivatedRangeActionsDuringState(stateMock2)).thenReturn(Set.of(rangeActionMock4));

        List<RangeAction<?>> result1 = raoResultWithCounterTradeRangeActions.getActivatedRangeActionsDuringState(stateMock1)
            .stream().sorted(Comparator.comparing(Object::toString)).collect(Collectors.toList());
        List<RangeAction<?>> result2 = raoResultWithCounterTradeRangeActions.getActivatedRangeActionsDuringState(stateMock2)
            .stream().sorted(Comparator.comparing(Object::toString)).collect(Collectors.toList());

        List<RangeAction<?>> expected1 = Set.of(rangeActionMock1, rangeActionMock3)
            .stream().sorted(Comparator.comparing(Object::toString)).collect(Collectors.toList());
        List<RangeAction<?>> expected2 = Set.of(rangeActionMock2, rangeActionMock4)
            .stream().sorted(Comparator.comparing(Object::toString)).collect(Collectors.toList());

        assertEquals(expected1.toString(), result1.toString());
        assertEquals(expected2.toString(), result2.toString());
    }
}
