package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.rao_runner.api.exceptions.RaoRunnerException;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.MalformedURLException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class FileImporterTest {

    @Autowired
    FileImporter fileImporter;

    @Test
    void checkIidmNetworkIsImportedCorrectly() {
        Network network = fileImporter.importNetwork(Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        assertEquals("UCTE", network.getSourceFormat());
        assertEquals(4, network.getCountryCount());
    }

    @Test
    void importNetworkThrowsException() {

        Assertions.assertThatThrownBy(() -> fileImporter.importNetwork("networkUrl"))
            .isInstanceOf(RaoRunnerException.class)
            .hasCauseInstanceOf(MalformedURLException.class)
            .hasMessageContaining("Exception occurred while retrieving file name from : networkUrl")
            .getCause()
            .hasMessageContaining("no protocol: networkUrl");
    }

    @Test
    void checkJsonCracIsImportedCorrectly() {
        Crac crac = fileImporter.importCrac(Objects.requireNonNull(getClass().getResource("/rao_inputs/crac.json")).toString());
        assertEquals("rao test crac", crac.getId());
        assertEquals(1, crac.getContingencies().size());
        assertEquals(11, crac.getFlowCnecs().size());
    }

    @Test
    void importCracThrowsException() {

        Assertions.assertThatThrownBy(() -> fileImporter.importCrac("cracUrl"))
            .isInstanceOf(RaoRunnerException.class)
            .hasCauseInstanceOf(MalformedURLException.class)
            .hasMessageContaining("Exception occurred while retrieving file name from : cracUrl")
            .getCause()
            .hasMessageContaining("no protocol: cracUrl");
    }

}
