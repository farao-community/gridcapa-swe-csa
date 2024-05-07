package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.google.common.base.Suppliers;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.ImportConfig;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.craccreation.creator.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.craccreation.creator.csaprofile.CsaProfileCrac;
import com.powsybl.openrao.data.craccreation.creator.csaprofile.craccreator.CsaProfileCracCreationContext;
import com.powsybl.openrao.data.craccreation.creator.csaprofile.craccreator.CsaProfileCracCreator;
import com.powsybl.openrao.data.craccreation.creator.csaprofile.importer.CsaProfileCracImporter;
import com.powsybl.openrao.data.craccreation.creator.csaprofile.parameters.CsaCracCreationParameters;
import com.powsybl.openrao.data.cracioapi.CracExporters;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

@Service
public class FileTestUtils {
    private final S3ArtifactsAdapter s3ArtifactsAdapter;

    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    public FileTestUtils(S3ArtifactsAdapter s3ArtifactsAdapter) {
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
    }

    public Network getNetworkFromResource(Path filePath) {
        Properties importParams = new Properties();
        //importParams.put("iidm.import.cgmes.cgm-with-subnetworks", false);
        return Network.read(filePath, LocalComputationManager.getDefault(), Suppliers.memoize(ImportConfig::load).get(), importParams);
    }

    public Crac importCrac(Path archiveTempPath, Network network, Instant utcInstant) {
        CsaProfileCracImporter cracImporter = new CsaProfileCracImporter();
        CsaProfileCrac nativeCrac;
        try {
            nativeCrac = cracImporter.importNativeCrac(new FileInputStream(archiveTempPath.toFile()));
        } catch (IOException e) {
            throw new CsaInternalException(e.getMessage());
        }
        CsaProfileCracCreator cracCreator = new CsaProfileCracCreator();
        CracCreationParameters cracCreationParameters = new CracCreationParameters();
        cracCreationParameters.addExtension(CsaCracCreationParameters.class, new CsaCracCreationParameters());
        CsaProfileCracCreationContext
        cracCreationContext = cracCreator.createCrac(nativeCrac, network, utcInstant.atOffset(ZoneOffset.UTC), cracCreationParameters);
        Crac crac = cracCreationContext.getCrac();

        /*Set<Contingency> allContingencies = crac.getContingencies();
        List<Contingency> contingenciesToExclude = new ArrayList<>(allContingencies).subList(5, allContingencies.size());

        Set<FlowCnec> allCnecs = crac.getFlowCnecs();
        List<FlowCnec> flowCnecsToRemove = allCnecs.stream()
            .filter(flowCnec -> !flowCnec.getState().isPreventive() && contingenciesToExclude.contains(flowCnec.getState().getContingency().get()))
            .collect(Collectors.toList());
        Set<String> flowCnecsToRemoveIds = new HashSet<>();
        flowCnecsToRemove.forEach(cnec -> flowCnecsToRemoveIds.add(cnec.getId()));
        crac.removeFlowCnecs(flowCnecsToRemoveIds);*/

        return crac;
    }
}