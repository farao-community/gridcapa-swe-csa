package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class MockCsaRequest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MockCsaRequest.class);

    private final MinioAdapter minioAdapter;

    public MockCsaRequest(MinioAdapter minioAdapter) {
        this.minioAdapter = minioAdapter;
    }

    public CsaRequest convertZipToCsaRequest(MultipartFile inputFilesArchive, Instant utcInstant) throws IOException {
        String taskId = UUID.randomUUID().toString();

        Path targetTmpUnzippedDir = Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"), "/profiles"));
        unzipFile(inputFilesArchive.getInputStream(), targetTmpUnzippedDir);

        CsaRequest.CommonProfiles commonProfiles = new CsaRequest.CommonProfiles();
        CsaRequest.Profiles frProfiles = new CsaRequest.Profiles();
        CsaRequest.Profiles esProfiles = new CsaRequest.Profiles();
        CsaRequest.Profiles ptProfiles = new CsaRequest.Profiles();

        // common profiles
        Optional<Path> tpbdProfileOpt = findFileFromPath(targetTmpUnzippedDir, "tpbdProfile");
        if (tpbdProfileOpt.isPresent()) {
            String tpbdProfileDestinationPath = String.format("%s/%s", taskId, tpbdProfileOpt.get().getFileName());
            minioAdapter.uploadInput(tpbdProfileDestinationPath, new FileInputStream(tpbdProfileOpt.get().toFile()));
            String tpbdProfileUrl = minioAdapter.generatePreSignedUrl(tpbdProfileDestinationPath);
            commonProfiles.setTpbdProfileUri(tpbdProfileUrl);
        }

        Optional<Path> eqbdProfileOpt = findFileFromPath(targetTmpUnzippedDir, "eqbdProfile");
        if (eqbdProfileOpt.isPresent()) {
            String eqbdProfileDestinationPath = String.format("%s/%s", taskId, eqbdProfileOpt.get().getFileName());
            minioAdapter.uploadInput(eqbdProfileDestinationPath, new FileInputStream(eqbdProfileOpt.get().toFile()));
            String eqbdProfileUrl = minioAdapter.generatePreSignedUrl(eqbdProfileDestinationPath);
            commonProfiles.setEqbdProfileUri(eqbdProfileUrl);
        }

        Optional<Path> svProfileOpt = findFileFromPath(targetTmpUnzippedDir, "svProfile");
        if (svProfileOpt.isPresent()) {
            String svProfileDestinationPath = String.format("%s/%s", taskId, svProfileOpt.get().getFileName());
            minioAdapter.uploadInput(svProfileDestinationPath, new FileInputStream(svProfileOpt.get().toFile()));
            String svbdProfileUrl = minioAdapter.generatePreSignedUrl(svProfileDestinationPath);
            commonProfiles.setSvProfileUri(svbdProfileUrl);
        }

        // FR profiles
        Optional<Path> tpProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "tpProfileFr");
        if (tpProfileFrOpt.isPresent()) {
            String tpProfileFrDestinationPath = String.format("%s/%s", taskId, tpProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(tpProfileFrDestinationPath, new FileInputStream(tpProfileFrOpt.get().toFile()));
            String tpProfileFrUrl = minioAdapter.generatePreSignedUrl(tpProfileFrDestinationPath);
            frProfiles.setTpProfileUri(tpProfileFrUrl);
        }

        Optional<Path> sshProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "sshProfileFr");
        if (sshProfileFrOpt.isPresent()) {
            String sshProfileFrDestinationPath = String.format("%s/%s", taskId, sshProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(sshProfileFrDestinationPath, new FileInputStream(sshProfileFrOpt.get().toFile()));
            String sshProfileFrUrl = minioAdapter.generatePreSignedUrl(sshProfileFrDestinationPath);
            frProfiles.setSshProfileUri(sshProfileFrUrl);
        }

        Optional<Path> eqProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "eqProfileFr");
        if (eqProfileFrOpt.isPresent()) {
            String eqProfileFrDestinationPath = String.format("%s/%s", taskId, eqProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(eqProfileFrDestinationPath, new FileInputStream(eqProfileFrOpt.get().toFile()));
            String eqProfileFrUrl = minioAdapter.generatePreSignedUrl(eqProfileFrDestinationPath);
            frProfiles.setEqProfileUri(eqProfileFrUrl);
        }

        Optional<Path> aeProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "aeProfileFr");
        if (aeProfileFrOpt.isPresent()) {
            String aeProfileFrDestinationPath = String.format("%s/%s", taskId, aeProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(aeProfileFrDestinationPath, new FileInputStream(aeProfileFrOpt.get().toFile()));
            String aeProfileFrUrl = minioAdapter.generatePreSignedUrl(aeProfileFrDestinationPath);
            frProfiles.setAeProfileUri(aeProfileFrUrl);
        }

        Optional<Path> coProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "coProfileFr");
        if (coProfileFrOpt.isPresent()) {
            String coProfileFrDestinationPath = String.format("%s/%s", taskId, coProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(coProfileFrDestinationPath, new FileInputStream(coProfileFrOpt.get().toFile()));
            String coProfileFrUrl = minioAdapter.generatePreSignedUrl(coProfileFrDestinationPath);
            frProfiles.setCoProfileUri(coProfileFrUrl);
        }

        Optional<Path> raProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "raProfileFr");
        if (raProfileFrOpt.isPresent()) {
            String raProfileFrDestinationPath = String.format("%s/%s", taskId, raProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(raProfileFrDestinationPath, new FileInputStream(raProfileFrOpt.get().toFile()));
            String raProfileFrUrl = minioAdapter.generatePreSignedUrl(raProfileFrDestinationPath);
            frProfiles.setRaProfileUri(raProfileFrUrl);
        }

        Optional<Path> erProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "erProfileFr");
        if (erProfileFrOpt.isPresent()) {
            String erProfileFrDestinationPath = String.format("%s/%s", taskId, erProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(erProfileFrDestinationPath, new FileInputStream(erProfileFrOpt.get().toFile()));
            String erProfileFrUrl = minioAdapter.generatePreSignedUrl(erProfileFrDestinationPath);
            frProfiles.setErProfileUri(erProfileFrUrl);
        }

        Optional<Path> ssiProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "ssiProfileFr");
        if (ssiProfileFrOpt.isPresent()) {
            String ssiProfileFrDestinationPath = String.format("%s/%s", taskId, ssiProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(ssiProfileFrDestinationPath, new FileInputStream(ssiProfileFrOpt.get().toFile()));
            String ssiProfileFrUrl = minioAdapter.generatePreSignedUrl(ssiProfileFrDestinationPath);
            frProfiles.setSsiProfileUri(ssiProfileFrUrl);
        }

        Optional<Path> sisProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "sisProfileFr");
        if (sisProfileFrOpt.isPresent()) {
            String sisProfileFrDestinationPath = String.format("%s/%s", taskId, sisProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(sisProfileFrDestinationPath, new FileInputStream(sisProfileFrOpt.get().toFile()));
            String sisProfileFrUrl = minioAdapter.generatePreSignedUrl(sisProfileFrDestinationPath);
            frProfiles.setSisProfileUri(sisProfileFrUrl);
        }

        Optional<Path> maProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "maProfileFr");
        if (maProfileFrOpt.isPresent()) {
            String maProfileFrDestinationPath = String.format("%s/%s", taskId, maProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(maProfileFrDestinationPath, new FileInputStream(maProfileFrOpt.get().toFile()));
            String maProfileFrFrUrl = minioAdapter.generatePreSignedUrl(maProfileFrDestinationPath);
            frProfiles.setMaProfileUri(maProfileFrFrUrl);
        }

        Optional<Path> smProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "smProfileFr");
        if (smProfileFrOpt.isPresent()) {
            String smProfileFrDestinationPath = String.format("%s/%s", taskId, smProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(smProfileFrDestinationPath, new FileInputStream(smProfileFrOpt.get().toFile()));
            String smProfileFrUrl = minioAdapter.generatePreSignedUrl(smProfileFrDestinationPath);
            frProfiles.setSmProfileUri(smProfileFrUrl);
        }

        Optional<Path> asProfileFrOpt = findFileFromPath(targetTmpUnzippedDir, "asProfileFr");
        if (asProfileFrOpt.isPresent()) {
            String asProfileFrDestinationPath = String.format("%s/%s", taskId, asProfileFrOpt.get().getFileName());
            minioAdapter.uploadInput(asProfileFrDestinationPath, new FileInputStream(asProfileFrOpt.get().toFile()));
            String asProfileFrUrl = minioAdapter.generatePreSignedUrl(asProfileFrDestinationPath);
            frProfiles.setAsProfileUri(asProfileFrUrl);
        }

        // ES profiles
        Optional<Path> tpProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "tpProfileEs");
        if (tpProfileEsOpt.isPresent()) {
            String tpProfileEsDestinationPath = String.format("%s/%s", taskId, tpProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(tpProfileEsDestinationPath, new FileInputStream(tpProfileEsOpt.get().toFile()));
            String tpProfileEsUrl = minioAdapter.generatePreSignedUrl(tpProfileEsDestinationPath);
            esProfiles.setTpProfileUri(tpProfileEsUrl);
        }

        Optional<Path> sshProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "sshProfileEs");
        if (sshProfileEsOpt.isPresent()) {
            String sshProfileEsDestinationPath = String.format("%s/%s", taskId, sshProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(sshProfileEsDestinationPath, new FileInputStream(sshProfileEsOpt.get().toFile()));
            String sshProfileEsUrl = minioAdapter.generatePreSignedUrl(sshProfileEsDestinationPath);
            esProfiles.setSshProfileUri(sshProfileEsUrl);
        }

        Optional<Path> eqProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "eqProfileEs");
        if (eqProfileEsOpt.isPresent()) {
            String eqProfileEsDestinationPath = String.format("%s/%s", taskId, eqProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(eqProfileEsDestinationPath, new FileInputStream(eqProfileEsOpt.get().toFile()));
            String eqProfileEsUrl = minioAdapter.generatePreSignedUrl(eqProfileEsDestinationPath);
            esProfiles.setEqProfileUri(eqProfileEsUrl);
        }

        Optional<Path> aeProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "aeProfileEs");
        if (aeProfileEsOpt.isPresent()) {
            String aeProfileEsDestinationPath = String.format("%s/%s", taskId, aeProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(aeProfileEsDestinationPath, new FileInputStream(aeProfileEsOpt.get().toFile()));
            String aeProfileEsUrl = minioAdapter.generatePreSignedUrl(aeProfileEsDestinationPath);
            esProfiles.setAeProfileUri(aeProfileEsUrl);
        }

        Optional<Path> coProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "coProfileEs");
        if (coProfileEsOpt.isPresent()) {
            String coProfileEsDestinationPath = String.format("%s/%s", taskId, coProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(coProfileEsDestinationPath, new FileInputStream(coProfileEsOpt.get().toFile()));
            String coProfileEsUrl = minioAdapter.generatePreSignedUrl(coProfileEsDestinationPath);
            esProfiles.setCoProfileUri(coProfileEsUrl);
        }

        Optional<Path> raProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "raProfileEs");
        if (raProfileEsOpt.isPresent()) {
            String raProfileEsDestinationPath = String.format("%s/%s", taskId, raProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(raProfileEsDestinationPath, new FileInputStream(raProfileEsOpt.get().toFile()));
            String raProfileEsUrl = minioAdapter.generatePreSignedUrl(raProfileEsDestinationPath);
            esProfiles.setRaProfileUri(raProfileEsUrl);
        }

        Optional<Path> erProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "erProfileEs");
        if (erProfileEsOpt.isPresent()) {
            String erProfileEsDestinationPath = String.format("%s/%s", taskId, erProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(erProfileEsDestinationPath, new FileInputStream(erProfileEsOpt.get().toFile()));
            String erProfileEsUrl = minioAdapter.generatePreSignedUrl(erProfileEsDestinationPath);
            esProfiles.setErProfileUri(erProfileEsUrl);
        }

        Optional<Path> ssiProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "ssiProfileEs");
        if (ssiProfileEsOpt.isPresent()) {
            String ssiProfileEsDestinationPath = String.format("%s/%s", taskId, ssiProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(ssiProfileEsDestinationPath, new FileInputStream(ssiProfileEsOpt.get().toFile()));
            String ssiProfileEsUrl = minioAdapter.generatePreSignedUrl(ssiProfileEsDestinationPath);
            esProfiles.setSsiProfileUri(ssiProfileEsUrl);
        }

        Optional<Path> sisProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "sisProfileEs");
        if (sisProfileEsOpt.isPresent()) {
            String sisProfileEsDestinationPath = String.format("%s/%s", taskId, sisProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(sisProfileEsDestinationPath, new FileInputStream(sisProfileEsOpt.get().toFile()));
            String sisProfileEsUrl = minioAdapter.generatePreSignedUrl(sisProfileEsDestinationPath);
            esProfiles.setSisProfileUri(sisProfileEsUrl);
        }

        Optional<Path> maProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "maProfileEs");
        if (maProfileEsOpt.isPresent()) {
            String maProfileEsDestinationPath = String.format("%s/%s", taskId, maProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(maProfileEsDestinationPath, new FileInputStream(maProfileEsOpt.get().toFile()));
            String maProfileEsUrl = minioAdapter.generatePreSignedUrl(maProfileEsDestinationPath);
            esProfiles.setMaProfileUri(maProfileEsUrl);
        }

        Optional<Path> smProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "smProfileEs");
        if (smProfileEsOpt.isPresent()) {
            String smProfileEsDestinationPath = String.format("%s/%s", taskId, smProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(smProfileEsDestinationPath, new FileInputStream(smProfileEsOpt.get().toFile()));
            String smProfileEsUrl = minioAdapter.generatePreSignedUrl(smProfileEsDestinationPath);
            esProfiles.setSmProfileUri(smProfileEsUrl);
        }

        Optional<Path> asProfileEsOpt = findFileFromPath(targetTmpUnzippedDir, "asProfileEs");
        if (asProfileEsOpt.isPresent()) {
            String asProfileEsDestinationPath = String.format("%s/%s", taskId, asProfileEsOpt.get().getFileName());
            minioAdapter.uploadInput(asProfileEsDestinationPath, new FileInputStream(asProfileEsOpt.get().toFile()));
            String asProfileEsUrl = minioAdapter.generatePreSignedUrl(asProfileEsDestinationPath);
            esProfiles.setAsProfileUri(asProfileEsUrl);
        }

        // PT profiles
        Optional<Path> tpProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "tpProfilePt");
        if (tpProfilePtOpt.isPresent()) {
            String tpProfilePtDestinationPath = String.format("%s/%s", taskId, tpProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(tpProfilePtDestinationPath, new FileInputStream(tpProfilePtOpt.get().toFile()));
            String tpProfilePtUrl = minioAdapter.generatePreSignedUrl(tpProfilePtDestinationPath);
            ptProfiles.setTpProfileUri(tpProfilePtUrl);
        }

        Optional<Path> sshProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "sshProfilePt");
        if (sshProfilePtOpt.isPresent()) {
            String sshProfilePtDestinationPath = String.format("%s/%s", taskId, sshProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(sshProfilePtDestinationPath, new FileInputStream(sshProfilePtOpt.get().toFile()));
            String sshProfilePtUrl = minioAdapter.generatePreSignedUrl(sshProfilePtDestinationPath);
            ptProfiles.setSshProfileUri(sshProfilePtUrl);
        }

        Optional<Path> eqProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "eqProfilePt");
        if (eqProfilePtOpt.isPresent()) {
            String eqProfilePtDestinationPath = String.format("%s/%s", taskId, eqProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(eqProfilePtDestinationPath, new FileInputStream(eqProfilePtOpt.get().toFile()));
            String eqProfilePtUrl = minioAdapter.generatePreSignedUrl(eqProfilePtDestinationPath);
            ptProfiles.setEqProfileUri(eqProfilePtUrl);
        }

        Optional<Path> aeProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "aeProfilePt");
        if (aeProfilePtOpt.isPresent()) {
            String aeProfilePtDestinationPath = String.format("%s/%s", taskId, aeProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(aeProfilePtDestinationPath, new FileInputStream(aeProfilePtOpt.get().toFile()));
            String aeProfilePtUrl = minioAdapter.generatePreSignedUrl(aeProfilePtDestinationPath);
            ptProfiles.setAeProfileUri(aeProfilePtUrl);
        }

        Optional<Path> coProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "coProfilePt");
        if (coProfilePtOpt.isPresent()) {
            String coProfilePtDestinationPath = String.format("%s/%s", taskId, coProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(coProfilePtDestinationPath, new FileInputStream(coProfilePtOpt.get().toFile()));
            String coProfilePtUrl = minioAdapter.generatePreSignedUrl(coProfilePtDestinationPath);
            ptProfiles.setCoProfileUri(coProfilePtUrl);
        }

        Optional<Path> raProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "raProfilePt");
        if (raProfilePtOpt.isPresent()) {
            String raProfilePtDestinationPath = String.format("%s/%s", taskId, raProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(raProfilePtDestinationPath, new FileInputStream(raProfilePtOpt.get().toFile()));
            String raProfilePtUrl = minioAdapter.generatePreSignedUrl(raProfilePtDestinationPath);
            ptProfiles.setRaProfileUri(raProfilePtUrl);
        }

        Optional<Path> erProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "erProfilePt");
        if (erProfilePtOpt.isPresent()) {
            String erProfilePtDestinationPath = String.format("%s/%s", taskId, erProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(erProfilePtDestinationPath, new FileInputStream(erProfilePtOpt.get().toFile()));
            String erProfilePtUrl = minioAdapter.generatePreSignedUrl(erProfilePtDestinationPath);
            ptProfiles.setErProfileUri(erProfilePtUrl);
        }

        Optional<Path> ssiProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "ssiProfilePt");
        if (ssiProfilePtOpt.isPresent()) {
            String ssiProfilePtDestinationPath = String.format("%s/%s", taskId, ssiProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(ssiProfilePtDestinationPath, new FileInputStream(ssiProfilePtOpt.get().toFile()));
            String ssiProfilePtUrl = minioAdapter.generatePreSignedUrl(ssiProfilePtDestinationPath);
            ptProfiles.setSsiProfileUri(ssiProfilePtUrl);
        }

        Optional<Path> sisProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "sisProfilePt");
        if (sisProfilePtOpt.isPresent()) {
            String sisProfilePtDestinationPath = String.format("%s/%s", taskId, sisProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(sisProfilePtDestinationPath, new FileInputStream(sisProfilePtOpt.get().toFile()));
            String sisProfilePtUrl = minioAdapter.generatePreSignedUrl(sisProfilePtDestinationPath);
            ptProfiles.setSisProfileUri(sisProfilePtUrl);
        }

        Optional<Path> maProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "maProfilePt");
        if (maProfilePtOpt.isPresent()) {
            String maProfilePtDestinationPath = String.format("%s/%s", taskId, maProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(maProfilePtDestinationPath, new FileInputStream(maProfilePtOpt.get().toFile()));
            String maProfilePtUrl = minioAdapter.generatePreSignedUrl(maProfilePtDestinationPath);
            ptProfiles.setMaProfileUri(maProfilePtUrl);
        }

        Optional<Path> smProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "smProfilePt");
        if (smProfilePtOpt.isPresent()) {
            String smProfilePtDestinationPath = String.format("%s/%s", taskId, smProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(smProfilePtDestinationPath, new FileInputStream(smProfilePtOpt.get().toFile()));
            String smProfilePtUrl = minioAdapter.generatePreSignedUrl(smProfilePtDestinationPath);
            ptProfiles.setSmProfileUri(smProfilePtUrl);
        }

        Optional<Path> asProfilePtOpt = findFileFromPath(targetTmpUnzippedDir, "asProfilePt");
        if (asProfilePtOpt.isPresent()) {
            String asProfilePtDestinationPath = String.format("%s/%s", taskId, asProfilePtOpt.get().getFileName());
            minioAdapter.uploadInput(asProfilePtDestinationPath, new FileInputStream(asProfilePtOpt.get().toFile()));
            String asProfilePtUrl = minioAdapter.generatePreSignedUrl(asProfilePtDestinationPath);
            ptProfiles.setAsProfileUri(asProfilePtUrl);
        }
        return new CsaRequest(taskId, utcInstant.toString(), commonProfiles, frProfiles, esProfiles, ptProfiles, minioAdapter.generatePreSignedUrl(String.format("%s/result/rao-schedule.json", taskId)));
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

    private Optional<Path> findFileFromPath(Path tempInputPath, String fileName) {
        try (Stream<Path> pathStream = Files.find(tempInputPath, 2, (path, basicFileAttributes) -> path.toFile().getName().contains(fileName))) {
            return pathStream.findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
