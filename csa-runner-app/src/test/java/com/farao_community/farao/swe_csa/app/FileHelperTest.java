package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class FileHelperTest {

    @Autowired
    FileHelper fileHelper;

    @MockBean
    MinioAdapter minioAdapter;

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
    void saveRaoParametersTest() {
        Mockito.when(minioAdapter.generatePreSignedUrl("id/rao-parameters/19990101_1230.json")).thenReturn("url");
        String result = fileHelper.uploadRaoParameters("id", OffsetDateTime.parse("1999-01-01T12:30Z").toInstant());
        assertEquals("url", result);
    }

    @Test
    void uploadJsonCracTest() {
        Path filePath = Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString());
        Network network = fileHelper.importNetwork(Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString()));
        Crac crac = fileHelper.importCrac(filePath, network, Instant.parse("2023-08-08T15:30:00Z"));
        Mockito.when(minioAdapter.generatePreSignedUrl("id/cracs/19990101_1230.json")).thenReturn("url");
        String result = fileHelper.uploadJsonCrac("id", crac, OffsetDateTime.parse("1999-01-01T12:30Z").toInstant());
        assertEquals("url", result);
    }

    @Test
    void uploadIidmNetworkToMinioTest() throws IOException {
        Network network = fileHelper.importNetwork(Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString()));
        Mockito.when(minioAdapter.generatePreSignedUrl("id/networks/19990101_1230.xiidm")).thenReturn("url");
        String result = fileHelper.uploadIidmNetworkToMinio("id", network, OffsetDateTime.parse("1999-01-01T12:30Z").toInstant());
        assertEquals("url", result);
    }

}
