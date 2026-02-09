package com.farao_community.farao.swe_csa.app.security_evaluator;

import com.farao_community.farao.swe_csa.app.FileImporter;
import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingValues;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyStepResult;
import com.farao_community.farao.swe_csa.app.dichotomy.ParallelDichotomiesResult;
import com.farao_community.farao.swe_csa.app.security_evaluator.MultiBorderMonitoringInput.CracRaoResultPair;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.raoresult.io.json.RaoResultJsonImporter;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.LoadFlowAndSensitivityParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SweCsaRaoResultValidatorTest {

    @Autowired
    FileImporter fileImporter;

    private final Logger LOGGER = LoggerFactory.getLogger(SweCsaRaoResultValidatorTest.class);
    private Network network;
    private Crac frEsCrac;
    private Crac ptEsCrac;
    private RaoResult frEsRaoResult;
    private RaoResult ptEsRaoResult;
    private ZonalData<Scalable> zonalScalable;
    private String loadFlowProvider;
    private LoadFlowParameters loadFlowParameters;

    @BeforeEach
    void prepareData() {
        network = fileImporter.importNetwork("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/TestCase_with_swe_countries.xiidm")).toString());
        frEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_fr_es_1.json")).toString(), network);
        ptEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_pt_es.json")).toString(), network);
        zonalScalable = fileImporter.getZonalData("taskId", java.time.Instant.parse("2017-04-13T07:00:00Z"), Objects.requireNonNull(getClass().getResource("/security_evaluator/glsk-document-cim.xml")).toString(), network);
        frEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_fr_es.json"), frEsCrac);
        ptEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_pt_es.json"), ptEsCrac);

        RaoParameters raoParameters = RaoParameters.load();
        loadFlowProvider =  LoadFlowAndSensitivityParameters.getLoadFlowProvider(raoParameters);
        loadFlowParameters = LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters();
    }

    ParallelDichotomiesResult runRaoResultValidation(Network network, Crac frEsCrac, Crac ptEsCrac, RaoResult frEsRaoResult, RaoResult ptEsRaoResult, ZonalData<Scalable> zonalScalable) {
        SweCsaRaoResultValidator sweCsaRaoResultValidator = new SweCsaRaoResultValidator(loadFlowProvider, loadFlowParameters, LOGGER);

        CounterTradingValues counterTradingValue = new CounterTradingValues(100, -100);
        DichotomyStepResult frEsDichotomyStepResult = DichotomyStepResult.fromNetworkValidationResult(frEsRaoResult, true, null, counterTradingValue);
        DichotomyStepResult ptEsDichotomyStepResult = DichotomyStepResult.fromNetworkValidationResult(ptEsRaoResult, true, null, counterTradingValue);

        // ParallelDichotomiesResult inverse the order of ptEs and frEs
        ParallelDichotomiesResult parallelDichotomiesResult = new ParallelDichotomiesResult(ptEsDichotomyStepResult, frEsDichotomyStepResult, counterTradingValue);
        return sweCsaRaoResultValidator.validateNetworkForTwoBorders(network, parallelDichotomiesResult, frEsCrac, ptEsCrac, zonalScalable);

    }

    @Test
    void twoBordersFlowCnecSecurityCheckerOKTest() {
        int numberOfLoadFlowsInParallel = 1;
        Map<Border, CracRaoResultPair> monitoringInputMap = Map.of(
                Border.FR_ES, new CracRaoResultPair(frEsCrac, frEsRaoResult),
                Border.PT_ES, new CracRaoResultPair(ptEsCrac, ptEsRaoResult)
        );
        MultiBorderMonitoringInput parallelInput = new MultiBorderMonitoringInput(network, monitoringInputMap, PhysicalParameter.FLOW, null, loadFlowProvider, loadFlowParameters);
        MultiBorderMonitoring flowCnecSecurityChecker = new MultiBorderMonitoring(parallelInput, numberOfLoadFlowsInParallel, LOGGER);

        Map<Border, MonitoringResult> flowSecurityCheck = flowCnecSecurityChecker.run();
        Map<Border, Boolean> flowSecurityPair = flowSecurityCheck.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getStatus() == Cnec.SecurityStatus.SECURE));

        assertTrue(flowSecurityPair.get(Border.FR_ES));
        assertFalse(flowSecurityPair.get(Border.PT_ES));
    }

    /**
     * The LF computation is failed
     * So the flow monitoring results are unsecure for both borders
     * */
    @Test
    void twoBordersFlowCnecSecurityCheckerWithDivergedLfTest() {
        network.getGeneratorStream().forEach(generator -> generator.setTargetP(-0.2));
        int numberOfLoadFlowsInParallel = 1;
        Map<Border, CracRaoResultPair> monitoringInputMap = Map.of(
                Border.FR_ES, new CracRaoResultPair(frEsCrac, frEsRaoResult),
                Border.PT_ES, new CracRaoResultPair(ptEsCrac, ptEsRaoResult)
        );
        MultiBorderMonitoringInput parallelInput = new MultiBorderMonitoringInput(network, monitoringInputMap, PhysicalParameter.FLOW, null, loadFlowProvider, loadFlowParameters);
        MultiBorderMonitoring flowCnecSecurityChecker = new MultiBorderMonitoring(parallelInput, numberOfLoadFlowsInParallel, LOGGER);

        Map<Border, MonitoringResult> flowSecurityCheck = flowCnecSecurityChecker.run();
        Map<Border, Boolean> flowSecurityPair = flowSecurityCheck.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getStatus() == Cnec.SecurityStatus.SECURE));

        assertFalse(flowSecurityPair.get(Border.FR_ES));
        assertFalse(flowSecurityPair.get(Border.PT_ES));
    }

    /**
     * Data description:
     * - One common CO in two Cracs
     * - Flow is secure for fr-es but not pt-es (given in raoResult.json files)
     * - 2 Angle CNECs, 2 Voltage CNECs in fr-es Crac (one at preventive and one at curative 3 for each type)
     * - pt-es Crac has 2 voltage CNECs, the "Voltage-Cnec-Pt-Es-1-curative 3" has very restricted constraints and is associated to "Network-action-pt-es-2"
     * */
    @Test
    void sweCsaRaoResultValidatorOKTest() {
        // Fixme: angleCnec.computeMargin() return Nan, angle cnec data need to be reviewed?

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation(network, frEsCrac, ptEsCrac, frEsRaoResult, ptEsRaoResult, zonalScalable);

        // Assert
        assertNotNull(validatedParallelDichotomiesResult);
        assertNotNull(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult());
        assertNotNull(validatedParallelDichotomiesResult.getPtEsResult().getRaoResult());
        assertTrue(validatedParallelDichotomiesResult.getFrEsResult().isSecure());
        assertFalse(validatedParallelDichotomiesResult.getPtEsResult().isSecure());
        assertTrue(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure());

        RaoResult validatedPtEsRaoResult = validatedParallelDichotomiesResult.getPtEsResult().getRaoResult();
        assertNotNull(validatedPtEsRaoResult);
        assertFalse(validatedPtEsRaoResult.isSecure());

        State ptEsCoCurative3 = ptEsCrac.getState("CO-Es-1", ptEsCrac.getInstant("curative 3"));
        List<NetworkAction> ptEsActivatedNetworkActions = validatedPtEsRaoResult.getActivatedNetworkActionsDuringState(ptEsCoCurative3).stream().toList();

        assertEquals(1, ptEsActivatedNetworkActions.size());
        assertEquals("Network-action-pt-es-2", ptEsActivatedNetworkActions.getFirst().getId());
    }

    /**
     * The LF computation is failed at the preventive flow monitoring
     * So the dichotomy results are unsecure for both borders
     * Note that the monitoring after preventive flow monitoring will not be executed
     * */
    @Test
    void sweCsaRaoResultValidatorWithDivergedLfTest() {
        // Fixme: data for angle cnec and maybe angle RAs
        // Extreme network to make LF failed
        network.getGeneratorStream().forEach(generator -> generator.setTargetP(-0.2));

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation(network, frEsCrac, ptEsCrac, frEsRaoResult, ptEsRaoResult, zonalScalable);

        // Assert
        assertNotNull(validatedParallelDichotomiesResult);
        assertNotNull(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult());
        assertNotNull(validatedParallelDichotomiesResult.getPtEsResult().getRaoResult());
        assertFalse(validatedParallelDichotomiesResult.getFrEsResult().isSecure());
        assertFalse(validatedParallelDichotomiesResult.getPtEsResult().isSecure());

    }

    /**
     * Decrease the thresholds of "Voltage-Cnec-Fr-Es-1-curative 3"
     * So that "Voltage-Cnec-Fr-Es-1-preventive" is secure
     * but "Voltage-Cnec-Fr-Es-1-curative 3" is unsecure at Curative 3
     * This result is updated in raoResult (in the input raoResult, the
     * margin of this CNEC is positive, but in the updated result, it is negative)
     * So the dichotomy of FrEs is unsecure
     * */
    @Test
    void raoResultValidatorWithUnsecureFrEsVoltageMonitoringAtCurative3Test() {

        frEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_fr_es_2.json")).toString(), network);
        frEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_fr_es.json"), frEsCrac);

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation(network, frEsCrac, ptEsCrac, frEsRaoResult, ptEsRaoResult, zonalScalable);
        // Assert
        assertNotNull(validatedParallelDichotomiesResult);
        assertNotNull(validatedParallelDichotomiesResult.getPtEsResult().getRaoResult());
        assertNotNull(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult());
        assertFalse(validatedParallelDichotomiesResult.getFrEsResult().isSecure());
        assertFalse(validatedParallelDichotomiesResult.getPtEsResult().isSecure());

        double inputVoltageMarginCurative3 = frEsRaoResult.getMargin(frEsCrac.getInstant("curative 3"), frEsCrac.getVoltageCnec("Voltage-Cnec-Fr-Es-1-curative 3"), Unit.KILOVOLT);
        double outputVoltageMarginCurative3 = validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().getMargin(frEsCrac.getInstant("curative 3"), frEsCrac.getVoltageCnec("Voltage-Cnec-Fr-Es-1-curative 3"), Unit.KILOVOLT);

        assertTrue(inputVoltageMarginCurative3 > 0);
        assertTrue(outputVoltageMarginCurative3 < 0);

    }
}
