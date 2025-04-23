/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.swe_csa.api;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.api.exception.AbstractCsaException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mohamed.ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class JsonApiConverterTest {

    @Test
    void checkCsaRequestJsonConversion() throws IOException {
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        byte[] requestBytes = getClass().getResourceAsStream("/csaRequestMessage.json").readAllBytes();
        CsaRequest request = jsonApiConverter.fromJsonMessage(requestBytes, CsaRequest.class);

        assertEquals("id", request.getId());
        assertEquals("2023-08-08T15:30:00Z", request.getBusinessTimestamp());
        assertEquals("https://cds/gridModelUri.signed.url", request.getGridModelUri());
        assertEquals("https://cds/ptEsCracFileUri.signed.url", request.getPtEsCracFileUri());
        assertEquals("https://cds/frEsCracFileUri.signed.url", request.getFrEsCracFileUri());
    }

    @Test
    void checkCsaResponseJsonConversion() throws IOException {
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        byte[] responseBytes = getClass().getResourceAsStream("/csaResponseMessage.json").readAllBytes();
        CsaResponse response = jsonApiConverter.fromJsonMessage(responseBytes, CsaResponse.class);

        assertEquals("id", response.getId());
        Assertions.assertEquals(Status.FINISHED_SECURE, response.getPtEsStatus());
        Assertions.assertEquals("pt-es-uri", response.getPtEsRaoResultUri());
        Assertions.assertEquals(Status.FINISHED_SECURE, response.getFrEsStatus());
        Assertions.assertEquals("fr-es-uri", response.getFrEsRaoResultUri());
    }

    @Test
    void checkExceptionJsonConversion() throws URISyntaxException, IOException {
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        AbstractCsaException exception = new CsaInternalException("id", "Something really bad happened");
        String expectedExceptionMessage = Files.readString(Paths.get(getClass().getResource("/errorMessage.json").toURI()));
        assertEquals(expectedExceptionMessage, new String(jsonApiConverter.toJsonMessage(exception)));

    }

}
