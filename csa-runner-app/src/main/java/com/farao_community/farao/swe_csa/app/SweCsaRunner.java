package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

@Service
public class SweCsaRunner {

    private final RaoRunnerClient raoRunnerClient;
    private final FileHelper fileHelper;
    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaRunner.class);

    public SweCsaRunner(RaoRunnerClient raoRunnerClient, FileHelper fileHelper) {
        this.raoRunnerClient = raoRunnerClient;
        this.fileHelper = fileHelper;
    }

    @Threadable
    public CsaResponse run(CsaRequest csaRequest) throws IOException {
        RaoResponse raoResponse = null;
        try {
            String requestId = csaRequest.getId();
            LOGGER.info("Csa request received : {}", csaRequest);
            Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
            String raoParametersUrl = fileHelper.uploadRaoParameters(requestId, utcInstant);
            RaoRequest raoRequest = new RaoRequest(requestId, csaRequest.getGridModelUri(), csaRequest.getCracFileUri(), raoParametersUrl);

            raoResponse = raoRunnerClient.runRao(raoRequest);
            LOGGER.info("RAO computation answer received for TimeStamp: '{}'", raoRequest.getInstant());

        } catch (Exception e) {
            throw new IOException(e);
        }
        return new CsaResponse(raoResponse.getId(), Status.FINISHED.toString());
    }

}
