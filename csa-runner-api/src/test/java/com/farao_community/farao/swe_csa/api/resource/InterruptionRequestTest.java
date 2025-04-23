package com.farao_community.farao.swe_csa.api.resource;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InterruptionRequestTest {

    @Test
    void testCsaRequestConstruction() {
        String id = "randomId";
        InterruptionRequest request = new InterruptionRequest(id);
        assertEquals(id, request.id());
    }
}
