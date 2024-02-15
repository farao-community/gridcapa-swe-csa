package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.app.FileHelper;
import com.farao_community.farao.swe_csa.app.dichotomy.index.SweCsaHalfRangeDivisionIndexStrategy;
import com.powsybl.iidm.network.Network;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.stream.Collectors;

@SpringBootTest
public class SweCsaDichotomyRunnerTest {

    @Autowired
    FileHelper fileHelper;

    @Autowired
    RaoRunnerClient raoRunnerClient;

    @Autowired
    SweCsaDichotomyRunner sweCsaDichotomyRunner;

    RaoResponse runWithFileAndTimestamp(String fileName, String timestamp) throws IOException {
        Path filePath = Paths.get(new File(getClass().getResource(fileName).getFile()).toString());
        Network network = fileHelper.importNetwork(Paths.get(new File(getClass().getResource(fileName).getFile()).toString()));
        Crac crac = fileHelper.importCrac(filePath, network, Instant.parse(timestamp));
        String networkFileUrl = fileHelper.uploadIidmNetworkToMinio("requestId", network, Instant.parse(timestamp));
        String cracFileUrl = fileHelper.uploadJsonCrac("requestId", crac, Instant.parse(timestamp));
        String raoParametersUrl = fileHelper.uploadRaoParameters("requestId", Instant.parse(timestamp));
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy = new SweCsaHalfRangeDivisionIndexStrategy(crac, network);
        SweCsaRaoValidator validator = new SweCsaRaoValidator(this.raoRunnerClient, "requestId", networkFileUrl, cracFileUrl, crac, raoParametersUrl,
            indexStrategy.getFrEsCnecs().stream().map(cnec -> cnec.getId()).collect(Collectors.toList()),
            indexStrategy.getPtEsCnecs().stream().map(cnec -> cnec.getId()).collect(Collectors.toList()));
        RaoResponse raoResponseAfterDichotomy = sweCsaDichotomyRunner.getDichotomyResponse(network, crac, validator, indexStrategy);
        return raoResponseAfterDichotomy;
    }
}
