package com.farao_community.farao.swe_csa.app.multi_border_monitoring;

import com.farao_community.farao.swe_csa.app.FileImporter;
import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingValues;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyStepResult;
import com.farao_community.farao.swe_csa.app.dichotomy.ParallelDichotomiesResult;
import com.farao_community.farao.swe_csa.app.multi_border_monitoring.MultiBorderMonitoringInput.CracRaoResultPair;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.api.rangeaction.RangeAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.raoresult.io.json.RaoResultJsonImporter;
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SweCsaRaoResultValidatorTest {

    @Autowired
    FileImporter fileImporter;

    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaRaoResultValidatorTest.class);
    private Network network;
    private Crac frEsCrac;
    private Crac ptEsCrac;
    private RaoResult frEsRaoResult;
    private RaoResult ptEsRaoResult;
    private ZonalData<Scalable> zonalScalable;
    private String loadFlowProvider;
    private LoadFlowParameters loadFlowParameters;

    //Note: GLSK is not perfect so try to avoid applying network action for angleCNEC (associated with redispatch actions)
    @BeforeEach
    void prepareData() {
        network = fileImporter.importNetwork("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/TestCase_with_swe_countries.xiidm")).toString());
        // frEsCrac: 2COs (one shared with ptEs), 2 FlowAE, 1 Angle AE and 1 VoltageAE, 1 Pst linked to the first FlowAE, 1 preventive SwitchRA available onInstant, 1 GenerationRA linked to the VoltageAE
        frEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_fr_es_1.json")).toString(), network);
        // ptEsCrac: 1CO shared with frEs, 1 FlowAE, 1 Angle AE and 1 VoltageAE, 1 Pst linked to the AngleAE, 1 curative SwitchRA linked to the voltageAE, 1 GenerationRA linked to the VoltageAE
        ptEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_pt_es_1.json")).toString(), network);
        zonalScalable = fileImporter.getZonalData("taskId", java.time.Instant.parse("2017-04-13T07:00:00Z"), Objects.requireNonNull(getClass().getResource("/security_evaluator/glsk-document-cim.xml")).toString(), network);
        // Results of flowCnecs, angleCnecs and VoltageCnecs, one NetworkRA activated at preventive, one PST activated at curative 1
        frEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_fr_es.json"), frEsCrac);
        // Results of flowCnecs, angleCnecs and VoltageCnecs, no RA activated
        ptEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_pt_es.json"), ptEsCrac);

        RaoParameters raoParameters = RaoParameters.load();
        loadFlowProvider =  LoadFlowAndSensitivityParameters.getLoadFlowProvider(raoParameters);
        loadFlowParameters = LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters();
    }

    private ParallelDichotomiesResult runRaoResultValidation() {
        SweCsaRaoResultValidator sweCsaRaoResultValidator = new SweCsaRaoResultValidator(loadFlowProvider, loadFlowParameters, LOGGER);

        CounterTradingValues counterTradingValue = new CounterTradingValues(100, -100);
        DichotomyStepResult frEsDichotomyStepResult = DichotomyStepResult.fromNetworkValidationResult(frEsRaoResult, true, null, counterTradingValue);
        DichotomyStepResult ptEsDichotomyStepResult = DichotomyStepResult.fromNetworkValidationResult(ptEsRaoResult, true, null, counterTradingValue);

        // ParallelDichotomiesResult inverse the order of ptEs and frEs
        ParallelDichotomiesResult parallelDichotomiesResult = new ParallelDichotomiesResult(ptEsDichotomyStepResult, frEsDichotomyStepResult, counterTradingValue);
        return sweCsaRaoResultValidator.validateNetworkForTwoBorders(network, parallelDichotomiesResult, frEsCrac, ptEsCrac, zonalScalable);

    }

    private MultiBorderMonitoring getFlowCnecSecurityChecker(Integer maxNrIterations) {
        if (maxNrIterations != null) {
            OpenLoadFlowParameters openLoadFlowParameters = new OpenLoadFlowParameters().setMaxNewtonRaphsonIterations(maxNrIterations);
            loadFlowParameters.addExtension(OpenLoadFlowParameters.class, openLoadFlowParameters);
        }
        int numberOfLoadFlowsInParallel = 1;
        Map<Border, CracRaoResultPair> monitoringInputMap = Map.of(
                Border.FR_ES, new CracRaoResultPair(frEsCrac, frEsRaoResult),
                Border.PT_ES, new CracRaoResultPair(ptEsCrac, ptEsRaoResult)
        );
        MultiBorderMonitoringInput parallelInput = new MultiBorderMonitoringInput(network, monitoringInputMap, PhysicalParameter.FLOW, null, loadFlowProvider, loadFlowParameters);
        return new MultiBorderMonitoring(parallelInput, numberOfLoadFlowsInParallel, LOGGER);
    }

    private void assertSecurity(ParallelDichotomiesResult validatedParallelDichotomiesResult, Boolean isFrEsSecure, Boolean isPtEsSecure) {
        assertNotNull(validatedParallelDichotomiesResult);
        assertNotNull(validatedParallelDichotomiesResult.getPtEsResult().getRaoResult());
        assertNotNull(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult());
        assertEquals(isFrEsSecure, validatedParallelDichotomiesResult.getFrEsResult().isSecure());
        assertEquals(isPtEsSecure, validatedParallelDichotomiesResult.getPtEsResult().isSecure());
    }

    @Test
    void twoBordersFlowCnecSecurityCheckerOKTest() {
        MultiBorderMonitoring flowCnecSecurityChecker = getFlowCnecSecurityChecker(30);
        MultiBorderMonitoringResult flowSecurityCheck = flowCnecSecurityChecker.run();
        assertThat(flowSecurityCheck.getMonitoringResultForBorder(Border.FR_ES).getStatus()).isEqualTo(Cnec.SecurityStatus.SECURE);
        assertThat(flowSecurityCheck.getMonitoringResultForBorder(Border.PT_ES).getStatus()).isEqualTo(Cnec.SecurityStatus.HIGH_CONSTRAINT);
    }

    /**
     * The LF computation is failed
     * So the flow monitoring results are unsecure for both borders
     * */
    @Test
    void twoBordersFlowCnecSecurityCheckerWithDivergedLfTest() {
        MultiBorderMonitoring flowCnecSecurityChecker = getFlowCnecSecurityChecker(1);
        MultiBorderMonitoringResult flowSecurityCheck = flowCnecSecurityChecker.run();
        assertThat(flowSecurityCheck.getMonitoringResultForBorder(Border.FR_ES).getStatus()).isEqualTo(Cnec.SecurityStatus.FAILURE);
        assertThat(flowSecurityCheck.getMonitoringResultForBorder(Border.PT_ES).getStatus()).isEqualTo(Cnec.SecurityStatus.FAILURE);
    }

    /**
     * Data description:
     * - One common CO in two Cracs
     * - Flow is secure for fr-es but not pt-es (given in raoResult.json files)
     * - 2 Angle CNECs, 2 Voltage CNECs in fr-es Crac (one at preventive and one at curative 3 for each type)
     * - pt-es Crac has 2 violated voltage CNECs,
     * "Network-action-pt-es-1" linked the preventive VoltageCNEC,
     * "Network-action-pt-es-2", "Pst-action-pt-es" linked to preventive and curative 3 voltageCNEC,
     * Expected result:
     * - Fr-Es secure
     * - Pt-Es unsecure (flowCnecs, angleCnecs, voltageCNECs unsecure)
     * - During voltageMontioring: PST is not applied, Network actions only applied at curative 3 (not preventive)
     * */
    @Test
    void sweCsaRaoResultValidatorOKTest() {

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();

        // Assert
        assertSecurity(validatedParallelDichotomiesResult, true, false);
        assertTrue(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure(PhysicalParameter.FLOW, PhysicalParameter.VOLTAGE, PhysicalParameter.ANGLE));

        // Pt-Es is not secure
        RaoResult validatedPtEsRaoResult = validatedParallelDichotomiesResult.getPtEsResult().getRaoResult();
        assertNotNull(validatedPtEsRaoResult);
        assertFalse(validatedPtEsRaoResult.isSecure(PhysicalParameter.FLOW, PhysicalParameter.ANGLE, PhysicalParameter.VOLTAGE));

        State stateCoCurative3 = ptEsCrac.getState("CO-Es-1", ptEsCrac.getInstant("curative 3"));
        List<NetworkAction> ptEsActivatedNetworkActionsAtCurative3 = validatedPtEsRaoResult.getActivatedNetworkActionsDuringState(stateCoCurative3).stream().toList();

        // "Network-action-pt-es-1" linked to VoltageCNEC at curative 3 is activated
        assertEquals(1, ptEsActivatedNetworkActionsAtCurative3.size());
        List<String> ptEsActivatedActionIds = ptEsActivatedNetworkActionsAtCurative3.stream().map(NetworkAction::getId).toList();
        assertTrue(ptEsActivatedActionIds.contains("Network-action-pt-es-1"));

        // Network action linked to VoltageCNEC at preventive ("Network-action-pt-es-2") is not activated
        List<NetworkAction> ptEsActivatedNetworkActionsAtPreventive = validatedPtEsRaoResult.getActivatedNetworkActionsDuringState(ptEsCrac.getPreventiveState()).stream().toList();
        assertEquals(0, ptEsActivatedNetworkActionsAtPreventive.size());

        // PST linked to VoltageCNEC is not activated
        List<RangeAction<?>> ptEsActivatedPstAtPreventive = validatedPtEsRaoResult.getActivatedRangeActionsDuringState(ptEsCrac.getPreventiveState()).stream().toList();
        assertEquals(0, ptEsActivatedPstAtPreventive.size());

        List<RangeAction<?>> ptEsActivatedPstAtCurative3 = validatedPtEsRaoResult.getActivatedRangeActionsDuringState(stateCoCurative3).stream().toList();
        assertEquals(0, ptEsActivatedPstAtCurative3.size());

        // "Network-action-fr-es-2" is not applied because its associated cnec "Voltage-Cnec-Fr-Es-1-curative 3" is not overloaded
        List<String> frEsActivatedActionIds = validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().getActivatedNetworkActionsDuringState(stateCoCurative3).stream().map(NetworkAction::getId).toList();
        assertFalse(frEsActivatedActionIds.contains("Network-action-fr-es-2"));
    }

    /**
     * The LF computation is failed at the preventive flow monitoring
     * So the dichotomy results are unsecure for both borders
     * Note that the monitoring after preventive flow monitoring will not be executed
     * */
    @Test
    void sweCsaRaoResultValidatorWithDivergedLf1Test() {
        // Extreme network to make LF failed
        network.getGeneratorStream().forEach(generator -> generator.setTargetP(-0.2));

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();

        // Assert
        assertSecurity(validatedParallelDichotomiesResult, false, false);

    }

    /**
     * The computation is failed at the when trying to redispatch the network
     * due to the bad input data of glsk document file
     * The angleMontioring at state "CO-Es-1" - Curative 3 failed for two borders
     * So Angle monitoring unsecure for both borders
     * */
    @Test
    void sweCsaRaoResultValidatorWithDivergedLf2Test() {
        // "Network-action-pt-es-2" has onConstraintUsage with AngleCNEC at preventive and curative 3
        ptEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_pt_es_2.json")).toString(), network);
        ptEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_pt_es.json"), ptEsCrac);

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();

        // Assert
        assertSecurity(validatedParallelDichotomiesResult, false, false);
        assertFalse(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure());
        assertFalse(validatedParallelDichotomiesResult.getPtEsResult().getRaoResult().isSecure());

    }

    /**
     * Decrease the thresholds of "Voltage-Cnec-Fr-Es-1-curative 3"
     * So that flow Cnecs and  "Voltage-Cnec-Fr-Es-1-preventive" are secure
     * but "Voltage-Cnec-Fr-Es-1-curative 3" is unsecure at Curative 3
     * This result is updated in raoResult (in the input raoResult, the
     * margin of this CNEC is positive, but in the updated result, it is negative)
     * So the dichotomy of FrEs is unsecure
     * */
    @Test
    void raoResultValidatorWithUnsecureFrEsVoltageMonitoringAtCurative3Test() {

        frEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_fr_es_2.json")).toString(), network);
        frEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_fr_es.json"), frEsCrac);

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();
        // Assert
        assertSecurity(validatedParallelDichotomiesResult, false, false);
        assertFalse(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure());
        assertFalse(validatedParallelDichotomiesResult.getPtEsResult().getRaoResult().isSecure());

        double inputVoltageMarginCurative3 = frEsRaoResult.getMargin(frEsCrac.getInstant("curative 3"), frEsCrac.getVoltageCnec("Voltage-Cnec-Fr-Es-1-curative 3"), Unit.KILOVOLT);
        double outputVoltageMarginCurative3 = validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().getMargin(frEsCrac.getInstant("curative 3"), frEsCrac.getVoltageCnec("Voltage-Cnec-Fr-Es-1-curative 3"), Unit.KILOVOLT);

        assertTrue(inputVoltageMarginCurative3 > 0);
        assertTrue(outputVoltageMarginCurative3 < 0);

    }

    /**
     * Fr-Es is secure at the beginning (data from sweCsaRaoResultValidatorOKTest)
     * Adding an overloaded angleCnec makes fr-es is not secure anymore
     * */
    @Test
    void frEsUnsecureDueToOverloadedAngleCnecsTest() {
        frEsCrac.newAngleCnec().withId("New-Angle-Cnec-Fr-Es").withBorder("ES-FR").withInstant("curative 3")
                .withContingency("CO-Fr-Es-2")
                .withExportingNetworkElement("FFR4AA11")
                .withImportingNetworkElement("FFR2AA11")
                .withOptimized(false).withMonitored(true).withReliabilityMargin(0.0)
                .newThreshold().withMax(1.0).withMin(-1.0).withUnit(Unit.DEGREE).add().add();

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();

        // Assert
        assertSecurity(validatedParallelDichotomiesResult, false, false);

        // Fr-Es is not secure
        RaoResult validatedFrEsRaoResult = validatedParallelDichotomiesResult.getFrEsResult().getRaoResult();
        assertNotNull(validatedFrEsRaoResult);
        assertTrue(validatedFrEsRaoResult.isSecure(PhysicalParameter.FLOW));
        assertFalse(validatedFrEsRaoResult.isSecure(PhysicalParameter.ANGLE));
        double newCnecMargin = validatedFrEsRaoResult.getMargin(frEsCrac.getInstant("curative 3"), frEsCrac.getAngleCnec("New-Angle-Cnec-Fr-Es"), Unit.DEGREE);
        assertTrue(newCnecMargin < 0);
    }

    /**
     * Fr-Es is secure at the beginning (data from sweCsaRaoResultValidatorOKTest)
     * Adding an overloaded angleCnec makes fr-es is not secure anymore
     * */
    @Test
    void frEsUnsecureDueToOverloadedVoltageCnecsTest() {
        frEsCrac.newVoltageCnec().withId("New-Voltage-Cnec-Fr-Es").withBorder("ES-FR").withInstant("curative 3")
                .withContingency("CO-Fr-Es-2").withNetworkElement("EES1AA1")
                .withOptimized(false).withMonitored(true).withReliabilityMargin(0.0)
                .newThreshold().withMax(1.0).withMin(-1.0).withUnit(Unit.KILOVOLT).add().add();

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();
        // Assert
        assertSecurity(validatedParallelDichotomiesResult, false, false);

        // Fr-Es is not secure
        RaoResult validatedFrEsRaoResult = validatedParallelDichotomiesResult.getFrEsResult().getRaoResult();
        assertNotNull(validatedFrEsRaoResult);
        assertTrue(validatedFrEsRaoResult.isSecure(PhysicalParameter.FLOW));
        assertTrue(validatedFrEsRaoResult.isSecure(PhysicalParameter.ANGLE));
        assertFalse(validatedFrEsRaoResult.isSecure(PhysicalParameter.VOLTAGE));
        double newCnecMargin = validatedFrEsRaoResult.getMargin(frEsCrac.getInstant("curative 3"), frEsCrac.getVoltageCnec("New-Voltage-Cnec-Fr-Es"), Unit.KILOVOLT);
        assertTrue(newCnecMargin < 0);
    }

}
