package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResultHelperTest {

    @Test
    void testUpdateRaoResultWithAngleMonitoring() {
        Network network = mock(Network.class);
        Crac crac = mock(Crac.class);
        ZonalData<Scalable> scalableZonalDataFilteredForSweCountries = mock(ZonalData.class);
        RaoResult raoResult = mock(RaoResult.class);
        RaoParameters raoParameters = mock(RaoParameters.class);

        ResultHelper resultHelper = new ResultHelper();
        RaoResult updatedRaoResult = resultHelper.updateRaoResultWithAngleMonitoring(network, crac, scalableZonalDataFilteredForSweCountries, raoResult, raoParameters);

        assertNotNull(updatedRaoResult);
    }

    @Test
    void testUpdateRaoResultWithVoltageMonitoring() {
        Network network = mock(Network.class);
        Crac crac = mock(Crac.class);
        RaoResult raoResult = mock(RaoResult.class);
        RaoParameters raoParameters = mock(RaoParameters.class);

        ResultHelper resultHelper = new ResultHelper();
        RaoResult updatedRaoResult = resultHelper.updateRaoResultWithVoltageMonitoring(network, crac, raoResult, raoParameters);

        assertNotNull(updatedRaoResult);
    }

    @Test
    void testUpdateRaoResultWithCounterTradingRangeActions() {
        Network network = mock(Network.class);
        Crac crac = mock(Crac.class);
        CounterTradeRangeAction counterTradeRangeActionPtEs = mock(CounterTradeRangeAction.class);
        when(counterTradeRangeActionPtEs.getId()).thenReturn("CT_RA_PTES");
        when(crac.getCounterTradeRangeAction("CT_RA_PTES")).thenReturn(counterTradeRangeActionPtEs);
        CounterTradeRangeAction counterTradeRangeActionEsPt = mock(CounterTradeRangeAction.class);
        when(counterTradeRangeActionEsPt.getId()).thenReturn("CT_RA_ESPT");
        when(crac.getCounterTradeRangeAction("CT_RA_ESPT")).thenReturn(counterTradeRangeActionEsPt);
        Index index = mock(Index.class);
        when(index.getPtEsLowestSecureStep()).thenReturn(Pair.of(100., null));
        RaoResult raoResult = mock(RaoResult.class);
        String border = "PT-ES";
        ResultHelper resultHelper = new ResultHelper();
        RaoResultWithCounterTradeRangeActions updatedRaoResult = resultHelper.updateRaoResultWithCounterTradingRangeActions(network, crac, index, raoResult, border);

        assertNotNull(updatedRaoResult);

    }
}
