/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.swe_csa.api.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mohamed.ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class CsaRequestTest {

    @Test
    void testCsaRequestConstruction() {
        String id = "sampleId";
        String businessTimestamp = "2023-08-08T15:30:00Z";
        String gridModelUri = "https://example.com/gridModel";
        String cracFileUri = "https://example.com/crac";

        CsaRequest request = new CsaRequest(id, businessTimestamp, gridModelUri, cracFileUri);
        assertEquals(id, request.getId());
        assertEquals(businessTimestamp, request.getBusinessTimestamp());
        assertEquals(gridModelUri, request.getGridModelUri());
        assertEquals(cracFileUri, request.getCracFileUri());
    }
}
