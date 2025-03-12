/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.swe_csa.api.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author mohamed.ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class CsaRequestTest {

    @Test
    void testCsaRequestConstruction() {
        String id = "sampleId";
        String businessTimestamp = "2023-08-08T15:30:00Z";
        String gridModelUri = "https://example.com/gridModel";
        String glskUri = "https://example.com/glsk";
        String ptEsCracFileUri = "https://example.com/ptEsCrac";
        String frEsCracFileUri = "https://example.com/frEsCrac";

        CsaRequest request = new CsaRequest(id, businessTimestamp, gridModelUri, glskUri, ptEsCracFileUri, frEsCracFileUri);
        assertEquals(id, request.getId());
        assertEquals(businessTimestamp, request.getBusinessTimestamp());
        assertEquals(gridModelUri, request.getGridModelUri());
        assertEquals(glskUri, request.getGlskUri());
        assertEquals(ptEsCracFileUri, request.getPtEsCracFileUri());
        assertEquals(frEsCracFileUri, request.getFrEsCracFileUri());
    }

    @Test
    void testEqualsAndHashCode() {
        CsaRequest request1 = new CsaRequest("1", "2025-03-11T12:00:00Z", "gridUri", "glskUri", "ptEsUri", "frEsUri");
        CsaRequest request2 = new CsaRequest("1", "2025-03-11T12:00:00Z", "gridUri", "glskUri", "ptEsUri", "frEsUri");
        CsaRequest request3 = new CsaRequest("2", "2025-03-11T12:00:00Z", "gridUri", "glskUri", "ptEsUri", "frEsUri");

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1, request3);
        assertNotEquals(request1.hashCode(), request3.hashCode());
        assertNotEquals(null, request1);
        assertNotEquals("some string", request1);
    }

}
