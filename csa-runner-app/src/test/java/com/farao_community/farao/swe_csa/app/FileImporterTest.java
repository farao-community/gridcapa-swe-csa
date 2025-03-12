package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.glsk.cim.CimGlskDocument;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
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
        Network network = fileImporter.importNetwork("taskId", Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        assertEquals("UCTE", network.getSourceFormat());
        assertEquals(4, network.getCountryCount());
    }

    @Test
    void importNetworkThrowsException() {
        Assertions.assertThatThrownBy(() -> fileImporter.importNetwork("taskId", "networkUrl"))
            .isInstanceOf(CsaInvalidDataException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Exception occurred while retrieving file name from : networkUrl")
            .cause()
            .hasMessageContaining("URI is not absolute");
    }

    @Test
    void checkJsonCracIsImportedCorrectly() {
        Network network = fileImporter.importNetwork("taskId", Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        Crac crac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/rao_inputs/crac.json")).toString(), network);
        assertEquals("rao test crac", crac.getId());
        assertEquals(1, crac.getContingencies().size());
        assertEquals(11, crac.getFlowCnecs().size());
    }

    @Test
    void importCracThrowsException() {
        Network network = fileImporter.importNetwork("taskId", Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        Assertions.assertThatThrownBy(() -> fileImporter.importCrac("taskId", "cracUrl", network))
            .isInstanceOf(CsaInvalidDataException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Exception occurred while retrieving file name from : cracUrl")
            .cause()
            .hasMessageContaining("URI is not absolute");
    }

    @Test
    void checkCimGlskIsImportedCorrectly() {
        Network testNetwork = Network.read("testCase.xiidm", getClass().getResourceAsStream("/glsk/testCase.xiidm"));
        CimGlskDocument cimGlskDocument = CimGlskDocument.importGlsk(getClass().getResourceAsStream("/glsk/glsk-document-cim.xml"));
        assertEquals(1, cimGlskDocument.getZones().size());
        Instant instant1 = Instant.parse("2017-04-13T07:00:00Z");
        ZonalData<Scalable> zonalScalables = cimGlskDocument.getZonalScalable(testNetwork, instant1);
        assertEquals(1, zonalScalables.getDataPerZone().size());
        Scalable scalableFR = zonalScalables.getData("10YFR-RTE------C");
        assertEquals(1, scalableFR.filterInjections(testNetwork).size());
        assertEquals("FFR3AA1 _generator", scalableFR.filterInjections(testNetwork).getFirst().getId());
    }

    @Test
    void saveRaoParametersTest() {
        Mockito.when(s3ArtifactsAdapter.generatePreSignedUrl("configurations/rao-parameters-19990101_1230.json")).thenReturn("url");
        String result = fileImporter.uploadRaoParameters(OffsetDateTime.parse("1999-01-01T12:30Z").toInstant());
        assertEquals("url", result);
    }
}
