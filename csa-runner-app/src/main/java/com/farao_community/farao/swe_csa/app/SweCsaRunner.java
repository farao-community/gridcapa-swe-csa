package com.farao_community.farao.swe_csa.app;

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

    private final DichotomyRunner dichotomyRunner;
    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaRunner.class);

    public SweCsaRunner(DichotomyRunner dichotomyRunner) {
        this.dichotomyRunner = dichotomyRunner;
    }

    @Threadable
    public CsaResponse run(CsaRequest csaRequest) throws IOException {
        try {
            LOGGER.info("Csa request received : {}", csaRequest);
            Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
            dichotomyRunner.runDichotomy(csaRequest);
            // TODO add counter trading range action and push file to results destination
            LOGGER.info("CSA computation finished for TimeStamp: '{}'", utcInstant.toString());
            return new CsaResponse(csaRequest.getId(), Status.FINISHED.toString());
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

}
