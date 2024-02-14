
package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.app.dichotomy.SweCsaDichotomyRunner;
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

    private final SweCsaRunner sweCsaRunner;
    private final SweCsaDichotomyRunner sweCsaDichotomyRunner;
    private final MockCsaRequest mockCsaRequest;
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();

    public CsaController(SweCsaRunner sweCsaRunner, SweCsaDichotomyRunner sweCsaDichotomyRunner, MockCsaRequest mockCsaRequest) {
        this.sweCsaRunner = sweCsaRunner;
        this.sweCsaDichotomyRunner = sweCsaDichotomyRunner;
        this.mockCsaRequest = mockCsaRequest;
    }

    @PostMapping(value = "/run", consumes = JSON_API_MIME_TYPE, produces = JSON_API_MIME_TYPE)
    public ResponseEntity<CsaResponse> runCsaByZip(@RequestPart byte[] jsonCsaRequest) throws IOException {
        return ResponseEntity.ok().body(sweCsaDichotomyRunner.runRaoDichotomy(jsonApiConverter.fromJsonMessage(jsonCsaRequest, CsaRequest.class)));
    }

    @PostMapping(value = "/run-single-rao", consumes = JSON_API_MIME_TYPE, produces = JSON_API_MIME_TYPE)
    public ResponseEntity<CsaResponse> runSingleRaoCsaByZip(@RequestPart byte[] jsonCsaRequest) throws IOException {
        return ResponseEntity.ok().body(sweCsaRunner.runSingleRao(jsonApiConverter.fromJsonMessage(jsonCsaRequest, CsaRequest.class)));
    }

    @PostMapping(value = "/convert-to-request", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}, produces = JSON_API_MIME_TYPE)
    public ResponseEntity<byte[]> convertZipToCsaRequest(@RequestPart MultipartFile inputFilesArchive,
                                      @RequestParam String utcInstant) throws IOException {
        Instant instant = Instant.parse(utcInstant);
        return ResponseEntity.ok().body(jsonApiConverter.toJsonMessage(mockCsaRequest.convertZipToCsaRequest(inputFilesArchive, instant), CsaRequest.class));
    }
}
