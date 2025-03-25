package com.farao_community.farao.swe_csa.app.dichotomy;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

import com.farao_community.farao.rao_runner.api.exceptions.RaoRunnerException;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.AbstractRaoResponse;
import com.farao_community.farao.rao_runner.api.resource.RaoFailureResponse;
import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.raoresult.io.json.RaoResultJsonImporter;
import com.powsybl.openrao.monitoring.Monitoring;
import com.powsybl.openrao.monitoring.MonitoringInput;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.LoadFlowAndSensitivityParameters;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SweCsaRaoValidator {

    private final FileExporter fileExporter;
    private final RaoRunnerClient raoRunnerClient;

    private final Logger businessLogger;

    public SweCsaRaoValidator(FileExporter fileExporter, RaoRunnerClient raoRunnerClient, Logger businessLogger) {
        this.fileExporter = fileExporter;
        this.raoRunnerClient = raoRunnerClient;
        this.businessLogger = businessLogger;
    }

    public DichotomyStepResult validateNetwork(Network network, Crac crac, ZonalData<Scalable> scalableZonalDataFilteredForSweCountries, RaoParameters raoParameters, CsaRequest csaRequest, String raoParametersUrl, CounterTradingValues counterTradingValues) {
        // FIXME MBR temporary workaround to test integration before crac separated feature is finished
        RaoRequest raoRequest = buildRaoRequest(counterTradingValues.print(), csaRequest.getBusinessTimestamp(), csaRequest.getId(), network, csaRequest.getPtEsCracFileUri(), raoParametersUrl);

        try {
            businessLogger.info("RAO request sent: {}", raoRequest);
            AbstractRaoResponse abstractRaoResponse = raoRunnerClient.runRao(raoRequest);
            businessLogger.info("RAO response received: {}", abstractRaoResponse);

            if (abstractRaoResponse.isRaoFailed()) {
                RaoFailureResponse raoFailureResponse = (RaoFailureResponse) abstractRaoResponse;
                throw new RaoRunnerException(raoFailureResponse.getErrorMessage());
            }

            RaoSuccessResponse raoSuccessResponse = (RaoSuccessResponse) abstractRaoResponse;
            RaoResult raoResult = new RaoResultJsonImporter().importData(new URL(raoSuccessResponse.getRaoResultFileUrl()).openStream(), crac);
            businessLogger.info("RAO result imported: {}", raoResult);

            raoResult = updateRaoResultWithAngleMonitoring(network, crac, scalableZonalDataFilteredForSweCountries, raoResult, raoParameters);

            Set<FlowCnec> frEsFlowCnecs = getBorderFlowCnecs(crac, network, Country.FR);
            Set<FlowCnec> ptEsFlowCnecs = getBorderFlowCnecs(crac, network, Country.PT);
            Pair<String, Double> flowCnecPtEsSmallestMargin = getFlowCnecSmallestMargin(raoResult, ptEsFlowCnecs);
            Pair<String, Double> flowCnecFrEsSmallestMargin = getFlowCnecSmallestMargin(raoResult, frEsFlowCnecs);

            return DichotomyStepResult.fromNetworkValidationResult(raoResult, raoSuccessResponse, flowCnecPtEsSmallestMargin, flowCnecFrEsSmallestMargin, counterTradingValues);
        } catch (Exception e) {
            throw new CsaInternalException(MDC.get("gridcapaTaskId"), "RAO run failed", e);
        }
    }

    private RaoResult updateRaoResultWithAngleMonitoring(Network network, Crac crac, ZonalData<Scalable> scalableZonalDataFilteredForSweCountries, RaoResult raoResult, RaoParameters raoParameters) {
        MonitoringInput angleMonitoringInput = MonitoringInput.buildWithAngle(network, crac, raoResult, scalableZonalDataFilteredForSweCountries).build();
        return Monitoring.runAngleAndUpdateRaoResult(LoadFlowAndSensitivityParameters.getLoadFlowProvider(raoParameters), LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters(), Runtime.getRuntime().availableProcessors(), angleMonitoringInput);
    }

    static Set<FlowCnec> getBorderFlowCnecs(Crac crac, Network network, Country country) {
        return crac.getFlowCnecs().stream()
            .filter(flowCnec -> flowCnec.isOptimized() && flowCnec.getLocation(network).contains(Optional.of(country)))
            .collect(Collectors.toSet());
    }

    Pair<String, Double> getFlowCnecSmallestMargin(RaoResult raoResult, Set<FlowCnec> flowCnecs) {
        String flowCnecId = "";
        double smallestMargin = Double.MAX_VALUE;
        for (FlowCnec flowCnec : flowCnecs) {
            double margin = raoResult.getMargin(flowCnec.getState().getInstant(), flowCnec, Unit.AMPERE);
            if (margin < smallestMargin) {
                flowCnecId = flowCnec.getId();
                smallestMargin = margin;
            }
        }
        return Pair.of(flowCnecId, smallestMargin);
    }

    private RaoRequest buildRaoRequest(String stepFolder, String timestamp, String taskId, Network network, String cracUrl, String raoParametersUrl) {
        String scaledNetworkPath = generateScaledNetworkPath(network, timestamp, stepFolder);
        String scaledNetworkPreSignedUrl = fileExporter.saveNetworkInArtifact(taskId, network, scaledNetworkPath);
        String raoResultDestination = generateArtifactsFolder(timestamp, stepFolder);
        return new RaoRequest.RaoRequestBuilder()
            .withId(taskId)
            .withRunId(taskId)
            .withNetworkFileUrl(scaledNetworkPreSignedUrl)
            .withCracFileUrl(cracUrl)
            .withRaoParametersFileUrl(raoParametersUrl)
            .withResultsDestination(raoResultDestination)
            .build();
    }

    private String generateArtifactsFolder(String timestamp, String stepFolder) {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp);
        return "artifacts" + "/" + offsetDateTime.getYear() + "/" + offsetDateTime.getMonthValue() + "/" + offsetDateTime.getDayOfMonth() + "/" + offsetDateTime.getHour() + "_" + offsetDateTime.getMinute() + "/" + stepFolder;
    }

    private String generateScaledNetworkPath(Network network, String timestamp, String stepFolder) {
        return generateArtifactsFolder(timestamp, stepFolder) + "/" + "network-" + network.getVariantManager().getWorkingVariantId() + ".xiidm";
    }
}
