
package com.farao_community.farao.swe_csa.app;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;

@RestController
@RequestMapping("/rao-integration")
@CrossOrigin(origins = "*")
public class CsaController {

    private static final String JSON_API_MIME_TYPE = "application/vnd.api+json";

    private final CsaRunner csaRunner;

    public CsaController(CsaRunner csaRunner) {
        this.csaRunner = csaRunner;
    }

    @PostMapping(value = "/run", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}, produces = JSON_API_MIME_TYPE)
    public ResponseEntity runDailyRao(@RequestPart MultipartFile inputFilesArchive,
                                       @RequestParam String utcInstant) throws IOException {
        Instant instant = Instant.parse(utcInstant);

        return ResponseEntity.ok().body(csaRunner.runRao(inputFilesArchive, instant));
    }
}
