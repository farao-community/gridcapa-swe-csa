
package com.farao_community.farao.gridcapa.swe_csa;

import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;


@RestController
@RequestMapping("/rao-integration")
public class CsaController {

    private static final String JSON_API_MIME_TYPE = "application/vnd.api+json";

    private final CsaService csaService;

    public CsaController(CsaService csaService) {
        this.csaService = csaService;
    }

    @PostMapping(value = "/run", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}, produces = JSON_API_MIME_TYPE)
    @ApiOperation(value = "Create and Start a  RAO computation task and returns it's status.", tags = "RAO computation")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "The RAO task has been created successfully."),
            @ApiResponse(code = 400, message = "Invalid inputs.")})
    public ResponseEntity runDailyRao(@ApiParam(value = "Input files ZIP archive") @RequestPart MultipartFile inputFilesArchive,
                                      @ApiParam(value = "UTC instant as a string") @RequestParam(name = "utcInstant") String utcInstant) throws IOException {
        Instant instant = Instant.parse(utcInstant);

        return ResponseEntity.ok().body(csaService.runRao(inputFilesArchive, instant));
    }
}
