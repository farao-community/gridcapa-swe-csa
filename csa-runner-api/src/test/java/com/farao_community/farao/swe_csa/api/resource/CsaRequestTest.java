/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.swe_csa.api.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author mohamed.ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class CsaRequestTest {

    @Test
    void testCsaRequestConstruction() {
        String id = "sampleId";
        String businessTimestamp = "2023-08-08T15:30:00Z";
        CsaRequest.CommonProfiles commonProfiles = new CsaRequest.CommonProfiles();
        commonProfiles.setTpbdProfileUri("https://example.com/tpbd");
        commonProfiles.setEqbdProfileUri("https://example.com/eqbd");
        commonProfiles.setSvProfileUri("https://example.com/sv");
        CsaRequest.Profiles frProfiles = new CsaRequest.Profiles();
        frProfiles.setSshProfileUri("https://example.com/ssh");

        String resultsUri = "https://example.com/results";

        CsaRequest request = new CsaRequest(id, businessTimestamp, commonProfiles, frProfiles, null, null, resultsUri);
        assertEquals(id, request.getId());
        assertEquals(businessTimestamp, request.getBusinessTimestamp());
        assertNotNull(request.getCommonProfiles());
        assertEquals("https://example.com/tpbd", request.getCommonProfiles().getTpbdProfileUri());
        assertEquals("https://example.com/eqbd", request.getCommonProfiles().getEqbdProfileUri());
        assertEquals("https://example.com/sv", request.getCommonProfiles().getSvProfileUri());
        assertNotNull(request.getFrProfiles());
        assertEquals("https://example.com/ssh", request.getFrProfiles().getSshProfileUri());

        assertEquals(resultsUri, request.getResultsUri());
    }
}
