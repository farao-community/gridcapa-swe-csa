package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

public class SweCsaRaoValidatorMock extends SweCsaRaoValidator {
    FileExporter fileExporter;
    RaoRunnerClient raoRunnerClient;

    public SweCsaRaoValidatorMock(FileExporter fileExporter, RaoRunnerClient raoRunnerClient) {
        super(fileExporter,
            raoRunnerClient, LoggerFactory.getLogger(SweCsaRaoValidatorMock.class));
        this.fileExporter = fileExporter;
        this.raoRunnerClient = raoRunnerClient;
    }

    @Override
    public DichotomyStepResult validateNetworkForFrenchBorder(Network network, Crac crac, String cracUri, ZonalData<Scalable> scalableZonalData, RaoParameters raoParameters, CsaRequest csaRequest, String raoParametersUrl, CounterTradingValues counterTradingValues) {
        RaoSuccessResponse raoResponse = Mockito.mock(RaoSuccessResponse.class);
        RaoResult raoResult = Mockito.mock(RaoResult.class);
        boolean isSecure = counterTradingValues.frEsCt() > 600;
        return DichotomyStepResult.fromNetworkValidationResult(raoResult, isSecure, raoResponse, counterTradingValues);
    }

    @Override
    public DichotomyStepResult validateNetworkForPortugueseBorder(Network network, Crac crac, String cracUri, ZonalData<Scalable> scalableZonalData, RaoParameters raoParameters, CsaRequest csaRequest, String raoParametersUrl, CounterTradingValues counterTradingValues) {
        RaoSuccessResponse raoResponse = Mockito.mock(RaoSuccessResponse.class);
        RaoResult raoResult = Mockito.mock(RaoResult.class);
        boolean isSecure = counterTradingValues.ptEsCt() >= 0;
        return DichotomyStepResult.fromNetworkValidationResult(raoResult, isSecure, raoResponse, counterTradingValues);
    }
}
