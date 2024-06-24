package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import org.apache.commons.lang3.tuple.Pair;
import org.mockito.Mockito;

public class SweCsaRaoValidatorMock extends SweCsaRaoValidator {
    FileExporter fileExporter;
    RaoRunnerClient raoRunnerClient;

    public SweCsaRaoValidatorMock(FileExporter fileExporter, RaoRunnerClient raoRunnerClient) {
        super(fileExporter,
            raoRunnerClient);
        this.fileExporter = fileExporter;
        this.raoRunnerClient = raoRunnerClient;
    }

    @Override
    public DichotomyStepResult validateNetwork(Network network, Crac crac, CsaRequest csaRequest, String raoParametersUrl, CounterTradingValues counterTradingValues) {
        RaoResponse raoResponse = Mockito.mock(RaoResponse.class);
        RaoResult raoResult = Mockito.mock(RaoResult.class);
        Pair<String, Double> ptEsMostLimitingCnec = Pair.of("ptEsCnec", counterTradingValues.getPtEsCt() >= 0 ? 100.0 : -100.0);
        Pair<String, Double> frEsMostLimitingCnec = Pair.of("frEsCnec", counterTradingValues.getFrEsCt() >= 600 ? 200.0 : -200.0);
        return DichotomyStepResult.fromNetworkValidationResult(raoResult, raoResponse, ptEsMostLimitingCnec, frEsMostLimitingCnec, counterTradingValues);
    }
}
