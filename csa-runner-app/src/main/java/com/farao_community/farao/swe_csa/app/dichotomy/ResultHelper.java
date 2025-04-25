package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.api.results.CounterTradingResult;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.iidm.network.Country;
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

public class ResultHelper {
    private final FileExporter fileExporter;

    public ResultHelper(FileExporter fileExporter) {
        this.fileExporter = fileExporter;
    }

    RaoResultWithCounterTradeRangeActions uploadRaoResultWithCounterTradeRangeActions(
        String raoResultDestinationPath,
        RaoParameters raoParameters,
        Network network,
        Crac crac,
        RaoResult raoResult,
        Index index,
        Country country) {

        RaoResult raoResultWithVoltage = applyVoltageMonitoring(network, crac, raoResult, raoParameters);
        RaoResultWithCounterTradeRangeActions raoResultWithRangeActions = addCounterTradingRangeActions(network, crac, index, raoResultWithVoltage, country);
        fileExporter.saveRaoResultInArtifact(raoResultDestinationPath, raoResultWithRangeActions, crac);
        return raoResultWithRangeActions;
    }

    private RaoResult applyVoltageMonitoring(Network network, Crac crac, RaoResult raoResult, RaoParameters raoParameters) {
        MonitoringInput input = MonitoringInput.buildWithVoltage(network, crac, raoResult).build();
        return Monitoring.runVoltageAndUpdateRaoResult(
            LoadFlowAndSensitivityParameters.getLoadFlowProvider(raoParameters),
            LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters(),
            Runtime.getRuntime().availableProcessors(),
            input
        );
    }

    private RaoResultWithCounterTradeRangeActions addCounterTradingRangeActions(
        Network network,
        Crac crac,
        Index index,
        RaoResult raoResult,
        Country country) {

        Map<CounterTradeRangeAction, CounterTradeRangeActionResult> resultMap = new HashMap<>();
        List<String> flowCnecs = SweCsaRaoValidator.getBorderFlowCnecs(crac, network, country)
            .stream()
            .map(Identifiable::getId)
            .toList();

        switch (country) {
            case PT -> {
                double value = Math.abs(index.getPtEsLowestSecureStep().getLeft());
                resultMap.put(crac.getCounterTradeRangeAction("CT_RA_PTES"), new CounterTradeRangeActionResult("CT_RA_PTES", value, flowCnecs));
                resultMap.put(crac.getCounterTradeRangeAction("CT_RA_ESPT"), new CounterTradeRangeActionResult("CT_RA_ESPT", value, flowCnecs));
            }
            case FR -> {
                double value = Math.abs(index.getFrEsLowestSecureStep().getLeft());
                resultMap.put(crac.getCounterTradeRangeAction("CT_RA_FRES"), new CounterTradeRangeActionResult("CT_RA_FRES", value, flowCnecs));
                resultMap.put(crac.getCounterTradeRangeAction("CT_RA_ESFR"), new CounterTradeRangeActionResult("CT_RA_ESFR", value, flowCnecs));
            }
            default -> throw new IllegalArgumentException("Unsupported country: " + country);
        }

        return new RaoResultWithCounterTradeRangeActions(raoResult, new CounterTradingResult(resultMap));
    }
}
