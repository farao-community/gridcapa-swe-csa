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

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.range_action.CounterTradeRangeAction;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.rao_result_json.RaoResultImporter;
import com.farao_community.farao.dichotomy.api.NetworkValidator;
import com.farao_community.farao.dichotomy.api.exceptions.ValidationException;
import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.api.results.CounterTradingResult;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class SweCsaRaoValidator implements NetworkValidator<RaoResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaRaoValidator.class);
    private final RaoRunnerClient raoRunnerClient;
    private String requestId;
    private String networkFileUrl;
    private String cracFileUrl;
    private Crac crac;
    private String raoParametersUrl;
    private MultipleDichotomyVariables counterTradingValue;
    private List<String> frEsCnecs;
    private List<String> ptEsCnecs;

    public SweCsaRaoValidator(RaoRunnerClient raoRunnerClient, String requestId, String networkFileUrl, String cracFileUrl, Crac crac, String raoParametersUrl, List<String> frEsCencs, List<String> ptEsCnecs) {
        this.raoRunnerClient = raoRunnerClient;
        this.requestId = requestId;
        this.networkFileUrl = networkFileUrl;
        this.cracFileUrl = cracFileUrl;
        this.crac = crac;
        this.raoParametersUrl = raoParametersUrl;
        this.frEsCnecs = frEsCencs;
        this.ptEsCnecs = ptEsCnecs;
    }

    @Override
    public DichotomyStepResult<RaoResponse> validateNetwork(Network network, DichotomyStepResult<RaoResponse> dichotomyStepResult) throws ValidationException {
        RaoRequest raoRequest = new RaoRequest(requestId, networkFileUrl, cracFileUrl, raoParametersUrl);
        try {
            LOGGER.info("RAO request sent: {}", raoRequest);
            RaoResponse raoResponse = raoRunnerClient.runRao(raoRequest);
            LOGGER.info("RAO response received: {}", raoResponse);
            RaoResult raoResult = new RaoResultImporter().importRaoResult(new URL(raoResponse.getRaoResultFileUrl()).openStream(), crac);
            RaoResultWithCounterTradeRangeActions raoResultWithCounterTradeRangeActions = this.createRaoResultWithCtRa(raoResult);
            return DichotomyStepResult.fromNetworkValidationResult(this.createRaoResultWithCtRa(raoResult), raoResponse);
        } catch (RuntimeException | IOException e) {
            throw new ValidationException("RAO run failed. Nested exception: " + e.getMessage());
        }
    }

    private RaoResultWithCounterTradeRangeActions createRaoResultWithCtRa(RaoResult raoResult) {
        CounterTradeRangeActionResult counterTradeRangeActionResultFrEs = new CounterTradeRangeActionResult(CounterTradingDirection.FR_ES.getName(),
            this.counterTradingValue.values().get(CounterTradingDirection.FR_ES.getName()), frEsCnecs);
        CounterTradeRangeActionResult counterTradeRangeActionResultPtEs = new CounterTradeRangeActionResult(CounterTradingDirection.PT_ES.getName(),
            this.counterTradingValue.values().get(CounterTradingDirection.PT_ES.getName()), ptEsCnecs);
        Map<CounterTradeRangeAction, CounterTradeRangeActionResult> counterTradeRangeActionResults = Map.of(
            crac.getCounterTradeRangeAction(CounterTradingDirection.FR_ES.getName()), counterTradeRangeActionResultFrEs,
            crac.getCounterTradeRangeAction(CounterTradingDirection.PT_ES.getName()), counterTradeRangeActionResultPtEs
        );
        CounterTradingResult counterTradingResult = new CounterTradingResult(counterTradeRangeActionResults);
        RaoResultWithCounterTradeRangeActions raoResultWithCounterTradeRangeActions = new RaoResultWithCounterTradeRangeActions(raoResult, counterTradingResult);
        return  raoResultWithCounterTradeRangeActions;
    }

    public void setCounterTradingValue(MultipleDichotomyVariables counterTradingValue) {
        this.counterTradingValue = counterTradingValue;
    }
}