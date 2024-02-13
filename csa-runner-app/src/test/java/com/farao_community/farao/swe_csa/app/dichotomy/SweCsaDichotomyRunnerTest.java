package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.app.FileHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
public class SweCsaDichotomyRunnerTest {

    @Autowired
    FileHelper fileHelper;

    @Autowired
    RaoRunnerClient raoRunnerClient;

    @Autowired
    SweCsaDichotomyRunner sweCsaDichotomyRunner;

    @Test
    void testRun() throws IOException {
        /*Path filePath = Paths.get(new File(getClass().getResource("/TestCase_1_14.zip").getFile()).toString());
        Network network = fileHelper.importNetwork(Paths.get(new File(getClass().getResource("/TestCase_1_14.zip").getFile()).toString()));
        Crac crac = fileHelper.importCrac(filePath, network, Instant.parse("2024-01-16T02:30:00Z"));
        String networkFileUrl = fileHelper.uploadIidmNetworkToMinio("requestId", network, Instant.parse("2024-01-16T02:30:00Z"));
        String cracFileUrl = fileHelper.uploadJsonCrac("requestId", crac, Instant.parse("2024-01-16T02:30:00Z"));
        String raoParametersUrl = fileHelper.uploadRaoParameters("requestId", Instant.parse("2024-01-16T02:30:00Z"));
        SweCsaRaoValidator validator = new SweCsaRaoValidator(this.raoRunnerClient, "requestId", networkFileUrl, cracFileUrl, crac, raoParametersUrl);

        RaoResponse raoResponseAfterDichotomy = sweCsaDichotomyRunner.getDichotomyResponse(network, crac, "2024-01-16T02:30:00Z", validator);

        assertNotNull(raoResponseAfterDichotomy);*/
    }
}
