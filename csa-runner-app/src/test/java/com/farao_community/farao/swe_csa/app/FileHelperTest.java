package com.farao_community.farao.swe_csa.app;

import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void testExportNetworkAndCrac() throws IOException {
        Path filePath = Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString());
        Network network = fileHelper.importNetwork(Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString()));
        Crac crac = fileHelper.importCrac(filePath, network, Instant.parse("2023-08-08T15:30:00Z"));
        String networkFileName = fileHelper.exportNetworkToFile(network, System.getProperty("java.io.tmpdir"));
        String cracFileName = fileHelper.exportCracToFile(crac, System.getProperty("java.io.tmpdir"));
        assertEquals(System.getProperty("java.io.tmpdir") + "/network.xiidm", networkFileName);
        assertEquals(System.getProperty("java.io.tmpdir") + "/crac.json", cracFileName);
        assertTrue(new File(System.getProperty("java.io.tmpdir") + "/network.xiidm").delete());
        assertTrue(new File(System.getProperty("java.io.tmpdir") + "/crac.json").delete());
    }
}
