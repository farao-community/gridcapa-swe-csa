package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyRunner;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import kotlin.Pair;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

@Service
public class SweCsaRunner {

    private final DichotomyRunner dichotomyRunner;
    private final Logger businessLogger;

    private final S3ArtifactsAdapter s3ArtifactsAdapter;

    public SweCsaRunner(DichotomyRunner dichotomyRunner, Logger businessLogger, S3ArtifactsAdapter s3ArtifactsAdapter) {
        this.dichotomyRunner = dichotomyRunner;
        this.businessLogger = businessLogger;
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
    }

    @Threadable
    public CsaResponse run(CsaRequest csaRequest) throws IOException {
        try {
            businessLogger.info("Csa request received : {}", csaRequest);
            Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
            Pair<RaoResult, Status> result = dichotomyRunner.runDichotomy(csaRequest);
            businessLogger.info("CSA computation finished for TimeStamp: '{}'", utcInstant);
            return new CsaResponse(csaRequest.getId(), result.getSecond().toString(), s3ArtifactsAdapter.generatePreSignedUrl(csaRequest.getResultsUri()));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

}
