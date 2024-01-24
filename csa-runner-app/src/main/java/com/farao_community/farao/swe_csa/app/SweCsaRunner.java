package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.range_action.CounterTradeRangeAction;
import com.farao_community.farao.data.crac_io_json.JsonImport;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.rao_result_json.RaoResultImporter;
import com.farao_community.farao.dichotomy.shift.ShiftDispatcher;
import com.farao_community.farao.dichotomy.shift.SplittingFactors;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyEngine;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyResult;
import com.farao_community.farao.swe_csa.app.dichotomy.HalfRangeDivisionIndexStrategy;
import com.farao_community.farao.swe_csa.app.dichotomy.Index;
import com.farao_community.farao.swe_csa.app.dichotomy.LinearScaler;
import com.farao_community.farao.swe_csa.app.dichotomy.MultipleDichotomyVariables;
import com.farao_community.farao.swe_csa.app.dichotomy.NetworkShifter;
import com.farao_community.farao.swe_csa.app.dichotomy.SingleDichotomyVariable;
import com.farao_community.farao.swe_csa.app.dichotomy.SweCsaRaoValidator;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.glsk.ucte.UcteGlskDocument;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityVariableSet;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class SweCsaRunner {

    private final RaoRunnerClient raoRunnerClient;
    private final FileHelper fileHelper;
    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaRunner.class);

    public SweCsaRunner(RaoRunnerClient raoRunnerClient, FileHelper fileHelper) {
        this.raoRunnerClient = raoRunnerClient;
        this.fileHelper = fileHelper;
    }

    @Threadable
    public CsaResponse runSingleRao(CsaRequest csaRequest) throws IOException {
        String requestId = csaRequest.getId();
        LOGGER.info("Csa request received : {}", csaRequest);
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        Path archiveTempPath = Files.createTempFile("csa-temp-inputs", "inputs.zip", attr);

        Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
        ZipHelper.zipDataCsaRequestFiles(csaRequest, archiveTempPath);
        Network network = fileHelper.importNetwork(archiveTempPath);
        Crac crac = fileHelper.importCrac(archiveTempPath, network, utcInstant);
        String networkFileUrl = fileHelper.uploadIidmNetworkToMinio(requestId, network, utcInstant);
        String cracFileUrl = fileHelper.uploadJsonCrac(requestId, crac, utcInstant);
        String raoParametersUrl = fileHelper.uploadRaoParameters(requestId, utcInstant);
        RaoRequest raoRequest = new RaoRequest(requestId, networkFileUrl, cracFileUrl, raoParametersUrl);

        RaoResponse raoResponse = raoRunnerClient.runRao(raoRequest);
        LOGGER.info("RAO computation answer received for TimeStamp: '{}'", raoRequest.getInstant());
        return new CsaResponse(raoResponse.getId(), Status.FINISHED.toString());
    }

    @Threadable
    public CsaResponse runRaoDichotomy(CsaRequest csaRequest) throws IOException {
        String requestId = csaRequest.getId();
        LOGGER.info("Csa request received : {}", csaRequest);
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        Path archiveTempPath = Files.createTempFile("csa-temp-inputs", "inputs.zip", attr);

        String timestamp = csaRequest.getBusinessTimestamp();
        Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
        ZipHelper.zipDataCsaRequestFiles(csaRequest, archiveTempPath);
        Network network = fileHelper.importNetwork(archiveTempPath);
        Crac crac = fileHelper.importCrac(archiveTempPath, network, utcInstant);
        String networkFileUrl = fileHelper.uploadIidmNetworkToMinio(requestId, network, utcInstant);
        String cracFileUrl = fileHelper.uploadJsonCrac(requestId, crac, utcInstant);
        String raoParametersUrl = fileHelper.uploadRaoParameters(requestId, utcInstant);
        RaoRequest raoRequest = new RaoRequest(requestId, networkFileUrl, cracFileUrl, raoParametersUrl);


        SweCsaRaoValidator validator = new SweCsaRaoValidator(raoRunnerClient, requestId, networkFileUrl, cracFileUrl, crac, raoParametersUrl);
        RaoResponse raoResultAfterDichotomy = launchDichotomy(network, crac, timestamp, validator);
        LOGGER.info("dichotomy RAO computation answer received for TimeStamp: '{}'", raoRequest.getInstant());
        return new CsaResponse(raoResultAfterDichotomy.getId(), Status.FINISHED.toString());
    }

    private RaoResponse launchDichotomy(Network network, Crac crac, String timestamp, SweCsaRaoValidator validator) throws IOException {

        Pair<MultipleDichotomyVariables,MultipleDichotomyVariables> initialDichotomyVariable = getInitialDichotomyIndex(crac);
        DichotomyEngine<RaoResponse, MultipleDichotomyVariables> engine = new DichotomyEngine<RaoResponse, MultipleDichotomyVariables>(
            new Index<>(initialDichotomyVariable.getLeft(), initialDichotomyVariable.getRight(), 10),
            new HalfRangeDivisionIndexStrategy<MultipleDichotomyVariables>(true),
            new LinearScaler(importGlskFile(timestamp, network), getCsaSweShiftDispatcher()),
            validator);
        DichotomyResult<RaoResponse, MultipleDichotomyVariables> result = engine.run(network);
        return result.getHighestValidStep().getValidationData();
    }

    private Pair<MultipleDichotomyVariables,MultipleDichotomyVariables> getInitialDichotomyIndex(Crac crac) {
        CounterTradeRangeAction ctRaFrEs = crac.getCounterTradeRangeAction("CT_RA_FRES");
        CounterTradeRangeAction ctRaEsFr = crac.getCounterTradeRangeAction("CT_RA_ESFR");
        CounterTradeRangeAction ctRaPtEs = crac.getCounterTradeRangeAction("CT_RA_PTES");
        CounterTradeRangeAction ctRaEsPt = crac.getCounterTradeRangeAction("CT_RA_ESPT");

        double expFrEs = ctRaFrEs.getInitialSetpoint();
        double expPtEs = ctRaPtEs.getInitialSetpoint();
        double expEsFr = ctRaEsFr.getInitialSetpoint();
        double expEsPt = ctRaEsPt.getInitialSetpoint();

        double ctFrEsMax = expFrEs>=0 ? Math.min(Math.min(-ctRaFrEs.getMinAdmissibleSetpoint(expFrEs), ctRaEsFr.getMaxAdmissibleSetpoint(expEsFr)), expFrEs)
            : Math.min(Math.min(ctRaFrEs.getMaxAdmissibleSetpoint(expFrEs), -ctRaEsFr.getMinAdmissibleSetpoint(expEsFr)), -expFrEs);
        double ctPtEsMax = expPtEs>=0 ? Math.min(Math.min(-ctRaPtEs.getMinAdmissibleSetpoint(expPtEs), ctRaEsPt.getMaxAdmissibleSetpoint(expEsPt)), expPtEs)
            : Math.min(Math.min(ctRaPtEs.getMaxAdmissibleSetpoint(expPtEs), -ctRaEsPt.getMinAdmissibleSetpoint(expEsPt)), -expPtEs);

        MultipleDichotomyVariables initMinIndex = new MultipleDichotomyVariables(Map.of("CT_FRES", 0.0, "CT_PTES", 0.0));
        MultipleDichotomyVariables initMaxIndex = new MultipleDichotomyVariables(Map.of("CT_FRES", ctFrEsMax, "CT_PTES", ctPtEsMax));

        return Pair.of(initMinIndex, initMaxIndex);
    }

    //TODO : import real splitting factors parameterization
    private ShiftDispatcher getCsaSweShiftDispatcher() {
        Map<String, Double> factors = Map.of("FR", 0.3, "ES", 0.5, "PT", 0.2);
        ShiftDispatcher csaSweShiftDispatcher = new SplittingFactors(factors);
        return csaSweShiftDispatcher;
    }

    private ZonalData<Scalable> importGlskFile(String timestamp, Network network) throws IOException {
        //TODO : import real glsk file
        File glskFile = new File(getClass().getResource("/10VCORSWEPR-ENDE_18V0000000005KUU_SWE-GLSK-B22-A48-F008_20220617-111.xml").getFile());
        InputStream inputStream = new FileInputStream(glskFile);
        UcteGlskDocument ucteGlskDocument = UcteGlskDocument.importGlsk(inputStream);
        return ucteGlskDocument.getZonalScalable(network, ucteGlskDocument.getGSKTimeInterval().getStart());
    }


}
