package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.api.results.CounterTradingResult;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Identifiable;
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.monitoring.Monitoring;
import com.powsybl.openrao.monitoring.MonitoringInput;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.LoadFlowAndSensitivityParameters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ResultHelper {

    public RaoResult updateRaoResultWithAngleMonitoring(Network network, Crac crac, ZonalData<Scalable> scalableZonalDataFilteredForSweCountries, RaoResult raoResult, RaoParameters raoParameters) {
        MonitoringInput angleMonitoringInput = MonitoringInput.buildWithAngle(network, crac, raoResult, scalableZonalDataFilteredForSweCountries).build();
        return Monitoring.runAngleAndUpdateRaoResult(LoadFlowAndSensitivityParameters.getLoadFlowProvider(raoParameters), LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters(), Runtime.getRuntime().availableProcessors(), angleMonitoringInput);
    }

    public RaoResult updateRaoResultWithVoltageMonitoring(Network network, Crac crac, RaoResult raoResult, RaoParameters raoParameters) {
        MonitoringInput input = MonitoringInput.buildWithVoltage(network, crac, raoResult).build();
        return Monitoring.runVoltageAndUpdateRaoResult(
            LoadFlowAndSensitivityParameters.getLoadFlowProvider(raoParameters),
            LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters(),
            Runtime.getRuntime().availableProcessors(),
            input
        );
    }

    public RaoResultWithCounterTradeRangeActions updateRaoResultWithCounterTradingRangeActions(
        Network network,
        Crac crac,
        Index index,
        RaoResult raoResult,
        String border) {

        Map<CounterTradeRangeAction, CounterTradeRangeActionResult> resultMap = new HashMap<>();
        List<String> flowCnecs = SweCsaRaoValidator.getBorderFlowCnecs(crac, border)
            .stream()
            .map(Identifiable::getId)
            .toList();

        switch (border) {
            case "PT-ES" -> {
                double value = Math.abs(index.getPtEsLowestSecureStep().getLeft());
                CounterTradeRangeAction ctRaPtes = Objects.requireNonNull(
                    crac.getCounterTradeRangeAction("CT_RA_PTES"),
                    "CRAC is missing CT_RA_PTES CT range action");
                resultMap.put(ctRaPtes, new CounterTradeRangeActionResult(ctRaPtes.getId(), value, flowCnecs));

                CounterTradeRangeAction ctRaEsPt = Objects.requireNonNull(
                    crac.getCounterTradeRangeAction("CT_RA_ESPT"),
                    "CRAC is missing CT_RA_ESPT CT range action");
                resultMap.put(ctRaEsPt, new CounterTradeRangeActionResult(ctRaEsPt.getId(), value, flowCnecs));
            }
            case "FR-ES" -> {
                double value = Math.abs(index.getFrEsLowestSecureStep().getLeft());
                CounterTradeRangeAction ctRaFrEs = Objects.requireNonNull(
                    crac.getCounterTradeRangeAction("CT_RA_FRES"),
                    "CRAC is missing CT_RA_FRES CT range action");
                resultMap.put(ctRaFrEs, new CounterTradeRangeActionResult(ctRaFrEs.getId(), value, flowCnecs));
                CounterTradeRangeAction ctRaEsFr = Objects.requireNonNull(
                    crac.getCounterTradeRangeAction("CT_RA_ESFR"),
                    "CRAC is missing CT_RA_ESFR CT range action");
                resultMap.put(ctRaEsFr, new CounterTradeRangeActionResult(ctRaEsFr.getId(), value, flowCnecs));
            }
            default -> throw new IllegalArgumentException("Unsupported border: " + border);
        }

        return new RaoResultWithCounterTradeRangeActions(raoResult, new CounterTradingResult(resultMap));
    }
}
