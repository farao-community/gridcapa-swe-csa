package com.farao_community.farao.swe_csa.app.dichotomy;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.*;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.gridcapa_swe_commons.shift.CountryBalanceComputation;
import com.farao_community.farao.swe_csa.app.shift.ShiftDispatcher;
import com.farao_community.farao.swe_csa.app.shift.SweCsaZonalData;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.EICode;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.LoadFlowAndSensitivityParameters;
import org.junit.jupiter.api.Test;

import java.util.*;

class SweCsaNetworkShifterTest {

    @Test
    void testShiftExchangeValues() throws GlskLimitationException, ShiftingException {
        Network network = Network.read("/dichotomy/TestCase_with_swe_countries.xiidm", getClass().getResourceAsStream("/dichotomy/TestCase_with_swe_countries.xiidm"));
        ZonalData<Scalable> scalableZonalData = SweCsaZonalData.getZonalData(network);
        Map<String, Double> targetExchanges = Map.of(
            "ES_FR", 2020.,
            "ES_PT", 0.
        );
        Map<String, Double> scalingValues = Map.of(
            new EICode(Country.PT).getAreaCode(), 0.,
            new EICode(Country.FR).getAreaCode(), -8.,
            new EICode(Country.ES).getAreaCode(), 8.
        );

        Map<String, Double> initialNetPositions = CountryBalanceComputation.computeSweCountriesBalances(network, LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(RaoParameters.load()).getLoadFlowParameters());
        Map<String, Double> balance = CountryBalanceComputation.computeSweBordersExchanges(network);
        assertEquals(2012., balance.get("ES_FR"), 1);
        assertEquals(0, balance.get("ES_PT"), 1);

        new SweCsaNetworkShifter(scalableZonalData, 2012., 0., new ShiftDispatcher(initialNetPositions)).shiftExchangeValues(network, targetExchanges, scalingValues);
        Map<String, Double> newBalance = CountryBalanceComputation.computeSweBordersExchanges(network);
        assertEquals(2020., newBalance.get("ES_FR"), 1);
        assertEquals(0, newBalance.get("ES_PT"), 1);

    }

    @Test
    void testShiftExchangeValuesWithShiftingException() {
        Network network = Network.read("/dichotomy/TestCase_with_swe_countries.xiidm", getClass().getResourceAsStream("/dichotomy/TestCase_with_swe_countries.xiidm"));
        ZonalData<Scalable> scalableZonalData = SweCsaZonalData.getZonalData(network);
        Map<String, Double> targetExchanges = Map.of(
            "ES_FR", 100.0,
            "ES_PT", 200.0
        );
        Map<String, Double> scalingValues = Map.of(
            new EICode(Country.PT).getAreaCode(), 100.,
            new EICode(Country.FR).getAreaCode(), 400.,
            new EICode(Country.ES).getAreaCode(), -500.
        );
        ShiftDispatcher dispatcher = new ShiftDispatcher(Map.of(Country.ES.getName(), -1., Country.FR.getName(), 1., Country.PT.getName(), 2.));
        assertThrows(ShiftingException.class, () -> new SweCsaNetworkShifter(scalableZonalData, 100., 200., dispatcher).shiftExchangeValues(network, targetExchanges, scalingValues));
    }

    @Test
    void testUpdateScalingValuesWithMismatch() {
        Network network = Network.read("/dichotomy/TestCase_with_swe_countries.xiidm", getClass().getResourceAsStream("/dichotomy/TestCase_with_swe_countries.xiidm"));
        ZonalData<Scalable> scalableZonalData = SweCsaZonalData.getZonalData(network);
        Map<String, Double> scalingValues = new HashMap<>();
        scalingValues.put(new EICode(Country.ES).getAreaCode(), 50.0);
        scalingValues.put(new EICode(Country.FR).getAreaCode(), 30.0);
        scalingValues.put(new EICode(Country.PT).getAreaCode(), 20.0);
        Map<String, Double> mismatch = Map.of(
            "ES_FR", 5.0,
            "ES_PT", 5.0
        );

        ShiftDispatcher dispatcher = new ShiftDispatcher(Map.of(Country.ES.getName(), -1., Country.FR.getName(), 1., Country.PT.getName(), 2.));
        new SweCsaNetworkShifter(scalableZonalData, 100., 200., dispatcher).updateScalingValuesWithMismatch(scalingValues, mismatch);

        assertEquals(60, scalingValues.get(new EICode(Country.ES).getAreaCode()));
        assertEquals(25.0, scalingValues.get(new EICode(Country.FR).getAreaCode()));
        assertEquals(15.0, scalingValues.get(new EICode(Country.PT).getAreaCode()));
    }
}
