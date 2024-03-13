package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import org.apache.commons.io.FilenameUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ZipHelper {

    private ZipHelper() {
        //util shouldn't be constructed
    }

    public static void zipDataCsaRequestFiles(CsaRequest csaRequest, Path archiveTempPath) throws IOException {
        FileOutputStream fos = new FileOutputStream(archiveTempPath.toFile());
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        zipDataFile(csaRequest.getGridModelUri(), zipOut);
        zipDataFile(csaRequest.getCracFileUri(), zipOut);
        zipOut.close();
        fos.close();
    }

    public static void zipDataFile(String uriStr, ZipOutputStream zipOut) throws IOException {
        if (uriStr != null) {
            URL url = new URL(uriStr);
            try (InputStream fis = url.openStream()) {
                ZipEntry zipEntry = new ZipEntry(FilenameUtils.getName(url.getPath()));
                zipOut.putNextEntry(zipEntry);
                byte[] bytes = new byte[1024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zipOut.write(bytes, 0, length);
                }
            }
        }
    }
}
