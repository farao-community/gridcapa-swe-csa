package com.farao_community.farao.swe_csa.app.utils;

import java.io.*;
import java.util.Locale;

/**
 * @author Georg Haider {@literal <georg.haider at artelys.com>}
 */
public class TmpFile implements AutoCloseable {

    private final File tempFile;

    protected TmpFile(File tempFile) {
        this.tempFile = tempFile;
    }

    public File getTempFile() {
        return tempFile;
    }

    public static TmpFile create(String suffix) throws IOException {
        var f = File.createTempFile("farao.swe_csa", suffix + ".tmp");
        f.deleteOnExit();
        return new TmpFile(f);
    }

    public static TmpFile create(String suffix, InputStream inputData) throws IOException {
        var tmp = create(suffix);
        tmp.loadInputStream(inputData);
        return tmp;
    }

    public static TmpFile create(String suffix, File inputData) throws IOException {
        return create(suffix, new FileInputStream(inputData));
    }

    protected void loadInputStream(InputStream inputStream) throws IOException {
        try (var is = inputStream;
             OutputStream out = new FileOutputStream(tempFile)) {
            is.transferTo(out);
        }
    }

    @Override
    public void close() {
        try {
            this.tempFile.delete();
        } catch (Exception e) {
            // ignore
        }
    }

    public InputStream getReadStream() {
        try {
            return new FileInputStream(tempFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Temp file not found", e);
        }
    }

    public OutputStream getWriteStream() {
        try {
            return new FileOutputStream(tempFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Invalid temp file", e);
        }
    }

    public String getFileSize() {
        return this.tempFile.length()
                + " bytes ("
                + humanReadableByteCount(this.tempFile.length())
                + ")";
    }

    public static String humanReadableByteCount(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

}
