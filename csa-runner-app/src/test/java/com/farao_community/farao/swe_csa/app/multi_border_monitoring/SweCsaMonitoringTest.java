package com.farao_community.farao.swe_csa.app.multi_border_monitoring;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.rao_runner.api.resource.AbstractRaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.farao_community.farao.swe_csa.app.InterruptionService;
import com.farao_community.farao.swe_csa.app.dichotomy.*;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.farao_community.farao.swe_csa.app.shift.SweCsaZonalData;
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.contingency.LineContingency;
import com.farao_community.farao.swe_csa.app.multi_border_monitoring.MultiBorderMonitoringInput.BorderMonitoringInput;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class SweCsaMonitoringTest {

    @Autowired
    FileImporter fileImporter;

    @Mock
    FileImporter dataCheckImporter;

    @Mock
    FileExporter fileExporter;

    @Mock
    RaoRunnerClient raoRunnerClient;

    @Mock
    StreamBridge streamBridge;

    @Mock
    S3ArtifactsAdapter s3ArtifactsAdapter;

    @Mock
    InterruptionService interruptionService;

    @Autowired
    ParallelDichotomiesRunner parallelDichotomiesRunner;

    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaMonitoringTest.class);
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
        zonalScalable = SweCsaZonalData.getZonalData(network);
        // Results of flowCnecs, angleCnecs and VoltageCnecs, one NetworkRA activated at preventive, one PST activated at curative 1
        frEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_fr_es.json"), frEsCrac);
        // Results of flowCnecs, angleCnecs and VoltageCnecs, no RA activated
        ptEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_pt_es.json"), ptEsCrac);

        RaoParameters raoParameters = RaoParameters.load();
        loadFlowProvider =  LoadFlowAndSensitivityParameters.getLoadFlowProvider(raoParameters);
        loadFlowParameters = LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters();
    }

    private ParallelDichotomiesResult runRaoResultValidation() {
        SweCsaMonitoring sweCsaMonitoring = new SweCsaMonitoring(loadFlowProvider, loadFlowParameters, LOGGER);

        CounterTradingValues counterTradingValue = new CounterTradingValues(100, -100);
        DichotomyStepResult frEsDichotomyStepResult = DichotomyStepResult.fromNetworkValidationResult(frEsRaoResult, true, null, counterTradingValue);
        DichotomyStepResult ptEsDichotomyStepResult = DichotomyStepResult.fromNetworkValidationResult(ptEsRaoResult, true, null, counterTradingValue);

        // ParallelDichotomiesResult inverse the order of ptEs and frEs
        ParallelDichotomiesResult parallelDichotomiesResult = new ParallelDichotomiesResult(ptEsDichotomyStepResult, frEsDichotomyStepResult, counterTradingValue);
        return sweCsaMonitoring.validateNetworkForSweBorders(network, parallelDichotomiesResult, frEsCrac, ptEsCrac, zonalScalable);

    }

    private MultiBorderMonitoring getFlowCnecSecurityChecker(Integer maxNrIterations) {
        if (maxNrIterations != null) {
            OpenLoadFlowParameters openLoadFlowParameters = new OpenLoadFlowParameters().setMaxNewtonRaphsonIterations(maxNrIterations);
            loadFlowParameters.addExtension(OpenLoadFlowParameters.class, openLoadFlowParameters);
        }
        int numberOfLoadFlowsInParallel = 2;
        Set<BorderMonitoringInput> monitoringInputs = Set.of(
                new MultiBorderMonitoringInput.BorderMonitoringInput(Border.FR_ES, frEsCrac, frEsRaoResult),
                new MultiBorderMonitoringInput.BorderMonitoringInput(Border.PT_ES, ptEsCrac, ptEsRaoResult));
        MultiBorderMonitoringInput parallelInput =
                new MultiBorderMonitoringInput(network, monitoringInputs, PhysicalParameter.FLOW, null, loadFlowProvider, loadFlowParameters);
        return new MultiBorderMonitoring(parallelInput, numberOfLoadFlowsInParallel, LOGGER);
    }

    private void assertSecurity(ParallelDichotomiesResult validatedParallelDichotomiesResult, Boolean isFrEsSecure, Boolean isPtEsSecure) {
        assertNotNull(validatedParallelDichotomiesResult);
        assertNotNull(validatedParallelDichotomiesResult.getPtEsResult().getRaoResult());
        assertNotNull(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult());
        assertEquals(isFrEsSecure, validatedParallelDichotomiesResult.getFrEsResult().isSecure());
        assertEquals(isPtEsSecure, validatedParallelDichotomiesResult.getPtEsResult().isSecure());
    }

    /**
     * Check that the input data (network file, crac files) are coherent
     * and do not create any issue when being used by DichotomyRunner
     * */
    @Test
    void checkInputDataWithDichotomyRunnerTest() throws GlskLimitationException, ShiftingException {
        Instant utcInstant = Instant.parse("2023-09-13T09:30:00Z");

        Mockito.lenient().doNothing().when(s3ArtifactsAdapter).uploadFile(any(), any());
        Mockito.when(dataCheckImporter.uploadRaoParameters(utcInstant)).thenReturn("rao-parameters-url");
        Mockito.when(dataCheckImporter.importNetwork("csa-task-id", "cgm-url")).thenReturn(network);
        Mockito.when(dataCheckImporter.importCrac("csa-task-id", "pt-es-crac-url", network)).thenReturn(ptEsCrac);
        Mockito.when(dataCheckImporter.importCrac("csa-task-id", "fr-es-crac-url", network)).thenReturn(frEsCrac);
        Mockito.when(dataCheckImporter.getZonalData("csa-task-id", utcInstant, "glsk-url", network)).thenReturn(zonalScalable);
        Mockito.lenient().when(fileExporter.saveNetworkInArtifact(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn("scaled-network-url");
        AbstractRaoResponse raoResponse = Mockito.mock(AbstractRaoResponse.class);
        Mockito.lenient().when(raoRunnerClient.runRao(Mockito.any())).thenReturn(raoResponse);
        SweCsaRaoValidator sweCsaRaoValidator = new SweCsaRaoValidatorMock(fileExporter, raoRunnerClient);
        CsaRequest csaRequest = new CsaRequest("csa-task-id", "2023-09-13T09:30:00Z", "cgm-url", "glsk-url", "pt-es-crac-url", "fr-es-crac-url");

        DichotomyRunner sweCsaDichotomyRunner = new DichotomyRunner(sweCsaRaoValidator, dataCheckImporter, fileExporter, interruptionService, streamBridge, s3ArtifactsAdapter, LoggerFactory.getLogger(com.farao_community.farao.swe_csa.app.multi_border_monitoring.SweCsaMonitoringTest.class), parallelDichotomiesRunner);
        sweCsaDichotomyRunner.setIndexPrecision(50);
        sweCsaDichotomyRunner.setMaxDichotomiesByBorder(10);
        FinalResult finalResult = sweCsaDichotomyRunner.runDichotomy(csaRequest, "pt-es-rao-result-path", "fr-es-rao-result-path");
        Assertions.assertEquals(Status.FINISHED_UNSECURE, finalResult.ptEsResult().getRight());
        Assertions.assertEquals(Status.FINISHED_UNSECURE, finalResult.frEsResult().getRight());
    }

    /**
     * Flow monitoring is performed on both SWE borders.
     * FR-ES: Secure
     * PT-ES: Unsecure
     * */
    @Test
    void flowMonitoringFrEsSecurePtEsHighConstraintTest() {
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
    void flowMonitoringLfDivergenceTest() {
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
    void ptEsUnsecureVoltageRasTest() {

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();

        // Assert
        assertSecurity(validatedParallelDichotomiesResult, true, false);
        assertTrue(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure(PhysicalParameter.FLOW, PhysicalParameter.VOLTAGE, PhysicalParameter.ANGLE));

        // Pt-Es is not secure
        RaoResult validatedPtEsRaoResult = validatedParallelDichotomiesResult.getPtEsResult().getRaoResult();
        assertNotNull(validatedPtEsRaoResult);
        assertFalse(validatedPtEsRaoResult.isSecure(PhysicalParameter.FLOW, PhysicalParameter.ANGLE, PhysicalParameter.VOLTAGE));

        State ptEsStateCoCurative3 = ptEsCrac.getState("CO-Es-1", ptEsCrac.getInstant("curative 3"));
        List<NetworkAction> ptEsActivatedNetworkActionsAtCurative3 = validatedPtEsRaoResult.getActivatedNetworkActionsDuringState(ptEsStateCoCurative3).stream().toList();

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

        List<RangeAction<?>> ptEsActivatedPstAtCurative3 = validatedPtEsRaoResult.getActivatedRangeActionsDuringState(ptEsStateCoCurative3).stream().toList();
        assertEquals(0, ptEsActivatedPstAtCurative3.size());

        // "Network-action-fr-es-2" is not applied because its associated cnec "Voltage-Cnec-Fr-Es-1-curative 3" is not overloaded
        State frEsStateCoCurative3 = frEsCrac.getState("CO-Es-1", frEsCrac.getInstant("curative 3"));

        List<String> frEsActivatedActionIds = validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().getActivatedNetworkActionsDuringState(frEsStateCoCurative3).stream().map(NetworkAction::getId).toList();
        assertFalse(frEsActivatedActionIds.contains("Network-action-fr-es-2"));
    }


    /**
     * Data description:
     * - One common CO in two Cracs
     * - Flow is secure for fr-es but not pt-es (given in raoResult.json files)
     * - 2 Angle CNECs, 2 Voltage CNECs in fr-es Crac (one at preventive and one at curative 3 for each type)
     * - pt-es Crac has 2 violated voltage CNECs, and 2 violated angle CNECs
     * "Network-action-pt-es-1" linked the preventive VoltageCNEC and curative 3 voltageCNEC,
     * "Network-action-pt-es-2", "Pst-action-pt-es" linked the preventive angleCNEC and curative 3 angleCNEC,
     * Expected result:
     * - Fr-Es secure
     * - Pt-Es unsecure (flowCnecs unsecure, angleCnecs unsecure, voltageCNECs unsecure)
     * - During angleMonitoring: "Network-action-pt-es-2" applied at curative 3 (not preventive) (note that the network redispatching is failed),
     * "Pst-action-pt-es" is not applied
     * - During voltageMontioring: "Network-action-pt-es-1" applied at curative 3 (not preventive)
     * */
    @Test
    void ptEsUnsecureAngleAndVoltageRasTest() {
        // "Network-action-pt-es-2" has onConstraintUsage with AngleCNEC at preventive and curative 3
        ptEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_pt_es_2.json")).toString(), network);
        ptEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_pt_es.json"), ptEsCrac);
        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();

        // Assert
        assertSecurity(validatedParallelDichotomiesResult, true, false);
        assertTrue(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure(PhysicalParameter.FLOW, PhysicalParameter.VOLTAGE, PhysicalParameter.ANGLE));

        // Pt-Es is not secure
        RaoResult validatedPtEsRaoResult = validatedParallelDichotomiesResult.getPtEsResult().getRaoResult();
        assertNotNull(validatedPtEsRaoResult);
        assertFalse(validatedPtEsRaoResult.isSecure(PhysicalParameter.FLOW, PhysicalParameter.ANGLE, PhysicalParameter.VOLTAGE));

        State ptEsStateCoCurative3 = ptEsCrac.getState("CO-Es-1", ptEsCrac.getInstant("curative 3"));
        List<NetworkAction> ptEsActivatedNetworkActionsAtCurative3 = validatedPtEsRaoResult.getActivatedNetworkActionsDuringState(ptEsStateCoCurative3).stream().toList();

        // "Network-action-pt-es-1" linked to VoltageCNEC and "Network-action-pt-es-2" linked to AngleCNEC at curative 3 is activated
        assertEquals(2, ptEsActivatedNetworkActionsAtCurative3.size());
        List<String> ptEsActivatedActionIds = ptEsActivatedNetworkActionsAtCurative3.stream().map(NetworkAction::getId).toList();
        assertTrue(ptEsActivatedActionIds.contains("Network-action-pt-es-1"));
        assertTrue(ptEsActivatedActionIds.contains("Network-action-pt-es-2"));

        // Network action linked to VoltageCNEC("Network-action-pt-es-1") and to AngleCNEC ("Network-action-pt-es-2") is not activated at preventive
        List<NetworkAction> ptEsActivatedNetworkActionsAtPreventive = validatedPtEsRaoResult.getActivatedNetworkActionsDuringState(ptEsCrac.getPreventiveState()).stream().toList();
        assertEquals(0, ptEsActivatedNetworkActionsAtPreventive.size());

        // PST linked to AngleCNEC is not activated
        List<RangeAction<?>> ptEsActivatedPstAtPreventive = validatedPtEsRaoResult.getActivatedRangeActionsDuringState(ptEsCrac.getPreventiveState()).stream().toList();
        assertEquals(0, ptEsActivatedPstAtPreventive.size());

        List<RangeAction<?>> ptEsActivatedPstAtCurative3 = validatedPtEsRaoResult.getActivatedRangeActionsDuringState(ptEsStateCoCurative3).stream().toList();
        assertEquals(0, ptEsActivatedPstAtCurative3.size());

        // "Network-action-fr-es-2" is not applied because its associated cnec "Voltage-Cnec-Fr-Es-1-curative 3" is not overloaded
        State frEsStateCoCurative3 = frEsCrac.getState("CO-Es-1", frEsCrac.getInstant("curative 3"));

        List<String> frEsActivatedActionIds = validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().getActivatedNetworkActionsDuringState(frEsStateCoCurative3).stream().map(NetworkAction::getId).toList();
        assertFalse(frEsActivatedActionIds.contains("Network-action-fr-es-2"));
    }



    /**
     * Data description:
     * - One common CO in two Cracs
     * - Flow is secure in FR-ES (given in rao_result_fr_es.json files)
     * - Only "Flow-Cnec-Pt-Es-curative 3" is not secure at curative 3 in PT_ES (given in rao_result_pt_es_3.json)
     * - 2 Angle CNECs, 2 Voltage CNECs in fr-es Crac (one at preventive and one at curative 3 for each type)
     * - pt-es Crac has 2 secure voltage CNECs, and 1 secure angle CNEC at preventive and one violated angle CNEC at curative 3
     * "Network-action-pt-es-1" linked the secure voltageCNECs,
     * "Network-action-pt-es-2", "Pst-action-pt-es" linked the preventive angleCNEC and curative 3 angleCNEC,
     * Expected result:
     * - Fr-Es secure
     * - Pt-Es secure
     * - During flowMonitoring: "Flow-Cnec-Pt-Es-curative 3" is secure by two RAs from FR_ES raoResult
     * - During angleMonitoring: only "Network-action-pt-es-2" applied at curative 3 to secure the pt_es angleCNEC at curative 3,
     * "Pst-action-pt-es" is not applied
     * - During voltageMontioring: "Network-action-pt-es-1" is not applied because all voltageCNECs are secure
     * */
    @Test
    void sweBorderSecureMonitoringTest() {
        // "Network-action-pt-es-2" has onConstraintUsage with AngleCNEC at preventive and curative 3
        ptEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_pt_es_3.json")).toString(), network);
        ptEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_pt_es_3.json"), ptEsCrac);
        double flowPtEsCurative3Margin =  ptEsRaoResult.getMargin(ptEsCrac.getInstant("curative 3"), ptEsCrac.getFlowCnec("Flow-Cnec-Pt-Es-curative 3"), Unit.AMPERE);
        assertTrue(flowPtEsCurative3Margin < 0);
        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();

        // Assert
        assertSecurity(validatedParallelDichotomiesResult, true, true);

        // Pt-Es is secure - verify RaoResult is accessible
        RaoResult validatedPtEsRaoResult = validatedParallelDichotomiesResult.getPtEsResult().getRaoResult();
        assertNotNull(validatedPtEsRaoResult);

        State ptEsStateCoCurative3 = ptEsCrac.getState("CO-Es-1", ptEsCrac.getInstant("curative 3"));
        List<NetworkAction> ptEsActivatedNetworkActionsAtCurative3 = validatedPtEsRaoResult.getActivatedNetworkActionsDuringState(ptEsStateCoCurative3).stream().toList();

        // "Network-action-pt-es-2" linked to AngleCNEC at curative 3 is activated
        assertEquals(1, ptEsActivatedNetworkActionsAtCurative3.size());
        List<String> ptEsActivatedActionIds = ptEsActivatedNetworkActionsAtCurative3.stream().map(NetworkAction::getId).toList();
        assertTrue(ptEsActivatedActionIds.contains("Network-action-pt-es-2"));
    }

    /**
     * The computation is failed at the when trying to redispatch the network
     * due to the bad input data of glsk document file
     * The angleMontioring at state "CO-Es-1" - Curative 3 failed for two borders
     * So Angle monitoring unsecure for both borders
     * */
    @Test
    void monitoringAngleLfDivergenceTest() {
        // "Network-action-pt-es-2" has onConstraintUsage with AngleCNEC at preventive and curative 3
        ptEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_pt_es_2.json")).toString(), network);
        ptEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_pt_es.json"), ptEsCrac);
        zonalScalable = fileImporter.getZonalData("taskId", java.time.Instant.parse("2017-04-13T07:00:00Z"), Objects.requireNonNull(getClass().getResource("/security_evaluator/non-valid-glsk-document-cim.xml")).toString(), network);

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();

        // Assert
        assertSecurity(validatedParallelDichotomiesResult, false, false);
        assertFalse(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure(PhysicalParameter.ANGLE));
        assertFalse(validatedParallelDichotomiesResult.getPtEsResult().getRaoResult().isSecure(PhysicalParameter.ANGLE));
        // The borders are not secure for the angle monitoring, by consequence they will not be secure overall
        assertFalse(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure());
        assertFalse(validatedParallelDichotomiesResult.getPtEsResult().getRaoResult().isSecure());

    }

    /**
     * Decrease the thresholds of "Voltage-Cnec-Fr-Es-1-curative 3"
     * So that flow Cnecs and  "Voltage-Cnec-Fr-Es-1-preventive" are secure
     * but "Voltage-Cnec-Fr-Es-1-curative 3" is unsecure at Curative 3
     * This result is updated in raoResult (in the input raoResult, the
     * margin of this CNEC is positive, but in the updated result, it is negative)
     * Expected Results:
     * FR-ES: flow -> secure, angle -> secure, voltage -> unsecure
     * PT-ES: flow -> unsecure, angle -> unsecure, voltage -> unsecure
     * */
    @Test
    void sweBordersVoltageMonitoringUnsecureTest() {

        frEsCrac = fileImporter.importCrac("taskId", Objects.requireNonNull(getClass().getResource("/security_evaluator/crac_fr_es_2.json")).toString(), network);
        frEsRaoResult = new RaoResultJsonImporter().importData(getClass().getResourceAsStream("/security_evaluator/rao_result_fr_es.json"), frEsCrac);

        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();
        // Assert
        assertSecurity(validatedParallelDichotomiesResult, false, false);
        assertFalse(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure(PhysicalParameter.VOLTAGE));
        assertFalse(validatedParallelDichotomiesResult.getPtEsResult().getRaoResult().isSecure(PhysicalParameter.VOLTAGE));
        // The borders are not secure for the voltage monitoring, by consequence they will not be secure overall
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
     * Adding an overloaded voltageCnec makes fr-es is not secure anymore
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

    /**
     * frEsCrac contains an invalid CO, there are some flowCnecs linked to it
     * This CO is not applied during the flow monitoring
     * So the flow of frEs is not secure
     * */
    @Test
    void invalidCoFlowMonitoringTest() {
        LineContingency lineContingency = new LineContingency("invalid_line", null);
        frEsCrac.getContingency("CO-Fr-Es-2").addElement(lineContingency);
        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();
        // Assert
        assertSecurity(validatedParallelDichotomiesResult, false, false);
    }

    /**
     * frEsCrac contains an invalid CO, and 'new_voltage_cnec' is linked to it
     * This CO is not applied during the voltage monitoring
     * So the voltage of frEs is not secure
     * */
    @Test
    void invalidCoVoltageMonitoringTest() {
        // Create a 'new_voltage_cnec' linked to an invalid CO
        frEsCrac.newContingency().withName("invalid_CO").withId("invalid_CO").withContingencyElement("invalid_CO_element", ContingencyElementType.LINE).add();
        frEsCrac.newVoltageCnec().withNetworkElement("FFR1AA1").withInstant("curative 3").withContingency("invalid_CO").withOptimized(false)
                .withMonitored(true).withId("new_voltage_cnec").newThreshold().withMax(300.0).withMin(-300.0).withUnit(Unit.KILOVOLT).add().withReliabilityMargin(0.0).add();
        ParallelDichotomiesResult validatedParallelDichotomiesResult = runRaoResultValidation();

        // Assert
        assertSecurity(validatedParallelDichotomiesResult, false, false);
        assertTrue(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure(PhysicalParameter.FLOW));
        assertFalse(validatedParallelDichotomiesResult.getFrEsResult().getRaoResult().isSecure(PhysicalParameter.VOLTAGE));
    }

}
