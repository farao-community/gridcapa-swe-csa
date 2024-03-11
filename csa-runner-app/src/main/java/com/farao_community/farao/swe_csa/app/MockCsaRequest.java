package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.app.s3.S3InputsAdapter;
import com.google.common.base.Suppliers;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.ImportConfig;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class MockCsaRequest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MockCsaRequest.class);

    private final S3InputsAdapter minioAdapter;

    private final FileHelper fileHelper;

    public MockCsaRequest(S3InputsAdapter minioAdapter, FileHelper fileHelper) {
        this.minioAdapter = minioAdapter;
        this.fileHelper = fileHelper;
    }

    public CsaRequest convertZipToCsaRequest(MultipartFile inputFilesArchive, Instant utcInstant) throws IOException {
        String taskId = UUID.randomUUID().toString();

        Path targetTmpUnzippedDir = Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"), "/inputFiles"));
        unzipFile(inputFilesArchive.getInputStream(), targetTmpUnzippedDir);

        Network network = Network.read(targetTmpUnzippedDir, LocalComputationManager.getDefault(), Suppliers.memoize(ImportConfig::load).get(), new Properties());
        Crac crac = fileHelper.importCrac(targetTmpUnzippedDir, network, utcInstant);

        String cracFileName = fileHelper.exportCracToFile(crac, targetTmpUnzippedDir.toString());
        String cracFileDestinationPath = taskId.concat("/crac.json");
        minioAdapter.uploadFile(cracFileDestinationPath, new FileInputStream(cracFileName));
        String cracUrl = minioAdapter.generatePreSignedUrl(cracFileDestinationPath);

        String networkFileName = fileHelper.exportNetworkToFile(network, targetTmpUnzippedDir.toString());
        String networkFileDestinationPath = taskId.concat("/network.iidm");
        minioAdapter.uploadFile(networkFileDestinationPath, new FileInputStream(networkFileName));
        String networkUrl = minioAdapter.generatePreSignedUrl(networkFileDestinationPath);

        return new CsaRequest(taskId, utcInstant.toString(), networkUrl, cracUrl, minioAdapter.generatePreSignedUrl(String.format("%s/result/rao-schedule.json", taskId)));
    }

    public static void unzipFile(InputStream zipFileIs, Path destDirectory) {
        if (!destDirectory.toFile().exists() && !destDirectory.toFile().mkdir()) {
            LOGGER.error("Cannot create destination directory '{}'", destDirectory);
            throw new RuntimeException(String.format("Cannot create destination directory '%s'", destDirectory));
        }
        try (ZipInputStream zipIn = new ZipInputStream(zipFileIs)) {
            ZipEntry entry = zipIn.getNextEntry(); //NOSONAR
            int totalEntries = 0;
            // iterates over entries in the zip file
            while (entry != null) {
                totalEntries++;
                Path filePath = Path.of(destDirectory + File.separator + entry.getName()).normalize();
                if (!filePath.startsWith(destDirectory)) {
                    throw new IOException("Entry is outside of the target directory");
                }
                if (!entry.isDirectory()) {
                    // if the entry is a file, extracts it
                    extractFile(zipIn, filePath.toString(), entry.getCompressedSize());
                } else {
                    // if the entry is a directory, make the directory
                    File dir = new File(filePath.toString()); //NOSONAR
                    dir.mkdir();
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry(); //NOSONAR
                if (totalEntries > 10000) {
                    throw new IOException("Entry threshold reached while unzipping.");
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error while extracting file");
            throw new RuntimeException("Error while extracting file", e);
        }
    }

    private static void extractFile(ZipInputStream zipIn, String filePath, long compressedSize) throws IOException {
        float totalSizeEntry = 0;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) { //NOSONAR
            byte[] bytesIn = new byte[4096];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
                totalSizeEntry += read;
                double compressionRatio = totalSizeEntry / compressedSize;
                if (compressionRatio > 100) {
                    throw new IOException("Ratio between compressed and uncompressed data suspiciously large.");
                }
            }
        }
    }
}
