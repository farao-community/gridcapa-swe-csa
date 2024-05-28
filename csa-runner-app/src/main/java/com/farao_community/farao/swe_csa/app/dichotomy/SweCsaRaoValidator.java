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

import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.AsynchronousRaoRunnerClient;
import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.cnec.FlowCnec;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import com.powsybl.openrao.data.raoresultjson.RaoResultImporter;
import com.powsybl.openrao.monitoring.voltagemonitoring.VoltageMonitoring;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class SweCsaRaoValidator {

    private final FileExporter fileExporter;
    private final AsynchronousRaoRunnerClient raoRunnerClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaRaoValidator.class);

    public SweCsaRaoValidator(FileExporter fileExporter, AsynchronousRaoRunnerClient raoRunnerClient) {
        this.fileExporter = fileExporter;
        this.raoRunnerClient = raoRunnerClient;
    }

    public DichotomyStepResult validateNetwork(Network network, Crac crac, CsaRequest csaRequest, String raoParametersUrl, boolean withVoltageMonitoring, boolean withAngleMonitoring, CounterTradingValues counterTradingValues) {
        RaoRequest raoRequest = buildRaoRequest(counterTradingValues.print(), csaRequest.getBusinessTimestamp(), csaRequest.getId(), network, csaRequest.getCracFileUri(), raoParametersUrl);

        LOGGER.info("RAO request sent: {}", raoRequest);
        CompletableFuture<RaoResponse> raoResponseFuture = raoRunnerClient.runRaoAsynchronously(raoRequest);
        RaoResult raoResult;
        RaoResponse raoResponse;
        try {
            try {
                raoResponse = raoResponseFuture.get();
                LOGGER.info("RAO response received: {}", raoResponse);
            } catch (ExecutionException e) {
                e.printStackTrace();
                raoResponse = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
                raoResponse = null;
                Thread.currentThread().interrupt();
            }
            raoResult = raoResponse == null ? null : new RaoResultImporter().importRaoResult(new URL(raoResponse.getRaoResultFileUrl()).openStream(), crac);
            LOGGER.info("RAO result imported: {}", raoResult);
        } catch (IOException ex) {
            throw new CsaInternalException("RAO run failed. Nested exception: " + ex.getMessage());
        }

        if (withVoltageMonitoring) {
            VoltageMonitoring voltageMonitoring = new VoltageMonitoring(crac, network, raoResult);
            raoResult = voltageMonitoring.runAndUpdateRaoResult(LoadFlow.find().getName(), LoadFlowParameters.load(), 1);
        }

        Set<FlowCnec> frEsFlowCnecs = getBorderFlowCnecs(crac, network, Country.FR);
        Set<FlowCnec> ptEsFlowCnecs = getBorderFlowCnecs(crac, network, Country.PT);
        Pair<String, Double> flowCnecPtEsShortestMargin = getFlowCnecShortestMargin(raoResult, ptEsFlowCnecs);
        Pair<String, Double> flowCnecFrEsShortestMargin = getFlowCnecShortestMargin(raoResult, frEsFlowCnecs);

        return DichotomyStepResult.fromNetworkValidationResult(raoResult, raoResponse, flowCnecPtEsShortestMargin, flowCnecFrEsShortestMargin, counterTradingValues);
    }

    static Set<FlowCnec> getBorderFlowCnecs(Crac crac, Network network, Country country) {
        return crac.getFlowCnecs().stream()
            .filter(flowCnec -> flowCnec.isOptimized() && flowCnec.getLocation(network).contains(Optional.of(country)))
            .collect(Collectors.toSet());
    }

    public Pair<String, Double> getFlowCnecShortestMargin(RaoResult raoResult, Set<FlowCnec> flowCnecs) {
        String flowCnecId = "";
        double shortestMargin = Double.MAX_VALUE;
        for (FlowCnec flowCnec : flowCnecs) {
            double margin = raoResult.getMargin(flowCnec.getState().getInstant(), flowCnec, Unit.AMPERE);
            if (margin < shortestMargin) {
                flowCnecId = flowCnec.getId();
                shortestMargin = margin;
            }
        }
        return Pair.of(flowCnecId, shortestMargin);
    }

    private RaoRequest buildRaoRequest(String stepFolder, String timestamp, String taskId, Network network, String cracUrl, String raoParametersUrl) {
        String scaledNetworkPath = generateScaledNetworkPath(network, timestamp, stepFolder);
        String scaledNetworkPreSignedUrl = fileExporter.saveNetworkInArtifact(network, scaledNetworkPath);
        String raoResultDestination = generateArtifactsFolder(timestamp, stepFolder);
        return new RaoRequest.RaoRequestBuilder()
            .withId(taskId)
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
