package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.rao_runner.api.exceptions.RaoRunnerException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.net.MalformedURLException;
import java.time.OffsetDateTime;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class FileImporterTest {

    @Autowired
    FileImporter fileImporter;

    @MockBean
    S3ArtifactsAdapter s3ArtifactsAdapter;

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
        Network network = fileImporter.importNetwork(Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        Crac crac = fileImporter.importCrac(Objects.requireNonNull(getClass().getResource("/rao_inputs/crac.json")).toString(), network);
        assertEquals("rao test crac", crac.getId());
        assertEquals(1, crac.getContingencies().size());
        assertEquals(11, crac.getFlowCnecs().size());
    }

    @Test
    void importCracThrowsException() {
        Network network = fileImporter.importNetwork(Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        Assertions.assertThatThrownBy(() -> fileImporter.importCrac("cracUrl", network))
            .isInstanceOf(RaoRunnerException.class)
            .hasCauseInstanceOf(MalformedURLException.class)
            .hasMessageContaining("Exception occurred while retrieving file name from : cracUrl")
            .getCause()
            .hasMessageContaining("no protocol: cracUrl");
    }

    @Test
    void saveRaoParametersTest() {
        Mockito.when(s3ArtifactsAdapter.generatePreSignedUrl("id/rao-parameters/19990101_1230.json")).thenReturn("url");
        String result = fileImporter.uploadRaoParameters(OffsetDateTime.parse("1999-01-01T12:30Z").toInstant());
        assertEquals("url", result);
    }
}
