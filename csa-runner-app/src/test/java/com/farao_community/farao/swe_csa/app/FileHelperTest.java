package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.data.crac_api.Crac;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class FileHelperTest {

    @Autowired
    FileHelper fileHelper;

    @Test
    void testImportNetworkAndCrac() {
        Path filePath = Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString());
        Network network = fileHelper.importNetwork(Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString()));
        Crac crac = fileHelper.importCrac(filePath, network, Instant.parse("2023-08-08T15:30:00Z"));

        assertEquals(1, crac.getContingencies().size());
        assertEquals(6, crac.getFlowCnecs().size());
        assertEquals(3, crac.getStates().size());
    }
}
