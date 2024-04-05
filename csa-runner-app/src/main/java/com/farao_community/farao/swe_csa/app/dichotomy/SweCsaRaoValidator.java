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

import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import com.powsybl.openrao.data.raoresultjson.RaoResultImporter;
import com.powsybl.openrao.monitoring.voltagemonitoring.VoltageMonitoring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.time.OffsetDateTime;

@Service
public class SweCsaRaoValidator {

    private final FileExporter fileExporter;
    private final FileImporter fileImporter;

    private final RaoRunnerClient raoRunnerClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaRaoValidator.class);

    public SweCsaRaoValidator(FileExporter fileExporter, FileImporter fileImporter, RaoRunnerClient raoRunnerClient) {
        this.fileExporter = fileExporter;
        this.fileImporter = fileImporter;
        this.raoRunnerClient = raoRunnerClient;
    }

    public DichotomyStepResult<RaoResponse> validateNetwork(Network network, CsaRequest csaRequest, String raoParametersUrl, boolean withVoltageMonitoring, boolean withAngleMonitoring) {
        RaoRequest raoRequest = buildRaoRequest(csaRequest.getBusinessTimestamp(), csaRequest.getId(), network, csaRequest.getCracFileUri(), raoParametersUrl);
        try {
            LOGGER.info("RAO request sent: {}", raoRequest);
            RaoResponse raoResponse = raoRunnerClient.runRao(raoRequest);
            LOGGER.info("RAO response received: {}", raoResponse);
            Crac crac = fileImporter.importCrac(csaRequest.getCracFileUri());
            RaoResult raoResult = new RaoResultImporter().importRaoResult(new URL(raoResponse.getRaoResultFileUrl()).openStream(), fileImporter.importCrac(csaRequest.getCracFileUri()));

            if (withVoltageMonitoring) {
                VoltageMonitoring voltageMonitoring = new VoltageMonitoring(crac, network, raoResult);
                raoResult = voltageMonitoring.runAndUpdateRaoResult(LoadFlow.find().getName(), LoadFlowParameters.load(), 1); // TODO number of LF in parallel
            }

            // TODO when csa glsk is ready
            if (withAngleMonitoring) {
                //AngleMonitoring angleMonitoring = new AngleMonitoring(crac, network, raoResult, CimGlskDocument.importGlsk());
                //raoResult = angleMonitoring.runAndUpdateRaoResult(LoadFlow.find().getName(), LoadFlowParameters.load(), 1, OffsetDateTime.parse(csaRequest.getBusinessTimestamp()));
            }

            return DichotomyStepResult.fromNetworkValidationResult(raoResult, raoResponse);
        } catch (RuntimeException | IOException e) {
            throw new CsaInternalException("RAO run failed. Nested exception: " + e.getMessage());
        }
    }

    private RaoRequest buildRaoRequest(String timestamp, String taskId, Network network, String cracUrl, String raoParametersUrl) {
        String scaledNetworkPath = generateScaledNetworkPath(network, timestamp);
        String scaledNetworkPreSignedUrl = fileExporter.saveNetworkInArtifact(network, scaledNetworkPath);
        String raoResultDestination = generateArtifactsFolder(timestamp);
        return new RaoRequest.RaoRequestBuilder()
            .withId(taskId)
            .withNetworkFileUrl(scaledNetworkPreSignedUrl)
            .withCracFileUrl(cracUrl)
            .withRaoParametersFileUrl(raoParametersUrl)
            .withResultsDestination(raoResultDestination)
            .build();
    }

    private String generateArtifactsFolder(String timestamp) {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp);
        return offsetDateTime.getYear() + "/" + offsetDateTime.getMonth() + "/" + offsetDateTime.getDayOfMonth() + offsetDateTime.getHour() + "_" + offsetDateTime.getMinute() + "/Artifacts/";
    }

    private String generateScaledNetworkPath(Network network, String timestamp) {
        return generateArtifactsFolder(timestamp) + network.getNameOrId() + network.getVariantManager().getWorkingVariantId() + ".xiidm";
    }
}
