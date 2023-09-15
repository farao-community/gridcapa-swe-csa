
package com.farao_community.farao.swe_csa.app;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/rao-integration")
@CrossOrigin(origins = "*")
public class CsaController {

    private static final String JSON_API_MIME_TYPE = "application/vnd.api+json";

    private final CsaRunner csaRunner;
    private final MockCsaRequest mockCsaRequest;

    public CsaController(CsaRunner csaRunner, MockCsaRequest mockCsaRequest) {
        this.csaRunner = csaRunner;
        this.mockCsaRequest = mockCsaRequest;
    }

    @PostMapping(value = "/run", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}, produces = JSON_API_MIME_TYPE)
    public ResponseEntity runCsaByZip(@RequestPart MultipartFile inputFilesArchive,
                                       @RequestParam String utcInstant) throws IOException, ExecutionException, InterruptedException {
        Instant instant = Instant.parse(utcInstant);

        return ResponseEntity.ok().body(csaRunner.runRao(inputFilesArchive, instant));
    }

    @PostMapping(value = "/convert-to-request", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}, produces = JSON_API_MIME_TYPE)
    public ResponseEntity convertZipToCsaRequest(@RequestPart MultipartFile inputFilesArchive,
                                      @RequestParam String utcInstant) throws IOException {
        Instant instant = Instant.parse(utcInstant);

        return ResponseEntity.ok().body(mockCsaRequest.convertZipToCsaRequest(inputFilesArchive, instant));
    }
}
