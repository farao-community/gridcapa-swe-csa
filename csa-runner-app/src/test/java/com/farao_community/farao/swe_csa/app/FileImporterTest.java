package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyRunner;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.LoadFlowAndSensitivityParameters;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class FileImporterTest {

    @Autowired
    FileImporter fileImporter;

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
        ZonalData<Scalable> zonalScalable = fileImporter.getZonalData("taskId", Instant.parse("2017-04-13T07:00:00Z"), Objects.requireNonNull(getClass().getResource("/glsk/glsk-document-cim.xml")).toString(), testNetwork);
        assertEquals(1, zonalScalable.getDataPerZone().size());
        Scalable scalableFR = zonalScalable.getData("10YFR-RTE------C");
        assertEquals(1, scalableFR.filterInjections(testNetwork).size());
        assertEquals("FFR3AA1 _generator", scalableFR.filterInjections(testNetwork).getFirst().getId());
    }

    @Test
    void checkGlskImportedBackup() {
        Network testNetwork = Network.read("testCase.xiidm", getClass().getResourceAsStream("/glsk/testCase.xiidm"));
        ZonalData<Scalable> zonalScalable = fileImporter.getZonalData("taskId", Instant.parse("2017-04-13T07:00:00Z"), "/mock.xml", testNetwork);
        assertEquals(4, zonalScalable.getDataPerZone().size());
        Scalable scalableFR = zonalScalable.getData("10YFR-RTE------C");
        assertEquals(3, scalableFR.filterInjections(testNetwork).size());
        assertEquals("FFR1AA1 _generator", scalableFR.filterInjections(testNetwork).getFirst().getId());
    }

    @Test
    void checkLoadFlowParametersAreImportedCorrectly() {
        LoadFlowParameters loadFlowParameters = fileImporter.getLoadFlowParameters("taskId", Objects.requireNonNull(getClass().getResource("/load_flow_parameters/load-flow-parameters.json")).toString());
        OpenLoadFlowParameters openLoadFlowParameters = loadFlowParameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(0.7, loadFlowParameters.getDcPowerFactor());
        assertEquals(7000, openLoadFlowParameters.getPlausibleActivePowerLimit());
    }

    @Test
    void checkLoadFlowParametersAreUpdatedInRaoParametersCorrectly() {
        RaoParameters raoParameters = RaoParameters.load();
        LoadFlowParameters defaultLoadFlowParameters = LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters();
        assertEquals(1.0, defaultLoadFlowParameters.getDcPowerFactor());
        LoadFlowParameters newLoadFlowParameters = fileImporter.getLoadFlowParameters("taskId", Objects.requireNonNull(getClass().getResource("/load_flow_parameters/load-flow-parameters.json")).toString());
        // Update loadFlowParameters in raoParameters
        DichotomyRunner.updateRaoParametersWithNewLoadFlowParameters(raoParameters, newLoadFlowParameters);
        LoadFlowParameters updatedLfParametersInRao = LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters();

        // Verify updated value of DCPowerFactor in LoadFlowParameters and PlausibleActivePowerLimit in OpenLoadFlowParameters (extension of LoadFlowParameters)
        assertEquals(0.7, updatedLfParametersInRao.getDcPowerFactor());
        assertEquals(7000, updatedLfParametersInRao.getExtension(OpenLoadFlowParameters.class).getPlausibleActivePowerLimit());
    }

}
