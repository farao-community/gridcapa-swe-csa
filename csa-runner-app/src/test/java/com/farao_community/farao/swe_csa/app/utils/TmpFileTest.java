package com.farao_community.farao.swe_csa.app.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TmpFileTest {

    @Test
    public void shouldLoadAndClose() throws IOException {
        File f;
        try (var tmp = TmpFile.create(".tmp")) {
            f = tmp.getTempFile();
            Assertions.assertTrue(f.exists());
            Assertions.assertEquals("0 bytes (0 B)", tmp.getFileSize());
        }
        Assertions.assertFalse(f.exists());
    }

    @Test
    public void shouldLoadFile() throws IOException {
        var res = getClass().getClassLoader().getResource("dummy.txt");
        try (var tmp = TmpFile.create(".tmp", new File(res.getFile()))) {
            Assertions.assertEquals("22 bytes (22 B)", tmp.getFileSize());
        }
    }

    @Test
    public void shouldLoadInputstream() throws IOException {
        var res = getClass().getClassLoader().getResourceAsStream("dummy.txt");
        try (var tmp = TmpFile.create(".tmp", res)) {
            Assertions.assertEquals("22 bytes (22 B)", tmp.getFileSize());
        }
    }

    @Test
    public void shouldRead() throws IOException {
        var res = getClass().getClassLoader().getResource("dummy.txt");
        try (var tmp = TmpFile.create(".tmp", new File(res.getFile()));
            var is = tmp.getReadStream();) {

            File out = null;
            try {
                out = File.createTempFile("test", ".tmp");
                Files.copy(is, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                String content = Files.readString(out.toPath());
                Assertions.assertEquals("TmpFileTest dummy file", content);
            } finally {
                out.delete();
            }
        }
    }

    @Test
    public void shouldWrite() throws IOException {
        var in = getClass().getClassLoader().getResource("dummy.txt");
        try (var tmp = TmpFile.create(".tmp");
            var os = tmp.getWriteStream()) {
            Files.copy(new File(in.getFile()).toPath(), os);
            String content = Files.readString(tmp.getTempFile().toPath());
            Assertions.assertEquals("TmpFileTest dummy file", content);
        }
    }

    @Test
    public void shouldPrettyPrint() throws IOException {
        Assertions.assertEquals("0 B", TmpFile.humanReadableByteCount(0));
        Assertions.assertEquals("12 B", TmpFile.humanReadableByteCount(12));
        Assertions.assertEquals("12.06 KB", TmpFile.humanReadableByteCount(12345));
        Assertions.assertEquals("11.50 GB", TmpFile.humanReadableByteCount(12345678910L));
    }

    @Test
    public void shouldFailGetReadStream() throws IOException {
        try (var tmp = TmpFile.create(".tmp")) {
            tmp.getTempFile().delete();
            Assertions.assertThrowsExactly(
                RuntimeException.class, tmp::getReadStream, "Temp file not found");
        }
    }
}
