package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyRunner;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

@Service
public class SweCsaRunner {

    private final DichotomyRunner dichotomyRunner;
    private final Logger businessLogger;

    public SweCsaRunner(DichotomyRunner dichotomyRunner, Logger businessLogger) {
        this.dichotomyRunner = dichotomyRunner;
        this.businessLogger = businessLogger;
    }

    @Threadable
    public CsaResponse run(CsaRequest csaRequest) throws IOException {
        try {
            businessLogger.info("Csa request received : {}", csaRequest);
            Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
            dichotomyRunner.runDichotomy(csaRequest);
            businessLogger.info("CSA computation finished for TimeStamp: '{}'", utcInstant);
            return new CsaResponse(csaRequest.getId(), Status.FINISHED.toString());
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

}
