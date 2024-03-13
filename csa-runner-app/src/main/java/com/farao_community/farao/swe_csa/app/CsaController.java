
package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
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
    private final MockCsaRequest mockCsaRequest;
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();

    public CsaController(SweCsaRunner sweCsaRunner, MockCsaRequest mockCsaRequest) {
        this.sweCsaRunner = sweCsaRunner;
        this.mockCsaRequest = mockCsaRequest;
    }

    @PostMapping(value = "/run", consumes = JSON_API_MIME_TYPE, produces = JSON_API_MIME_TYPE)
    public ResponseEntity runCsaByZip(@RequestPart byte[] jsonCsaRequest) throws IOException {
        return ResponseEntity.ok().body(sweCsaRunner.run(jsonApiConverter.fromJsonMessage(jsonCsaRequest, CsaRequest.class)));
    }

    @PostMapping(value = "/convert-to-request", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}, produces = JSON_API_MIME_TYPE)
    public ResponseEntity convertZipToCsaRequest(@RequestPart MultipartFile cracJson, @RequestPart MultipartFile networkIidm, @RequestParam String utcInstant) throws IOException {
        Instant instant = Instant.parse(utcInstant);
        return ResponseEntity.ok().body(jsonApiConverter.toJsonMessage(mockCsaRequest.makeRequest(cracJson, networkIidm, instant), CsaRequest.class));
    }
}
