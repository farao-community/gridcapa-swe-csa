/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.swe_csa.api.resource;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

class RaoRunnerLogsModelTest {
    @Test
    void testRaoRunnerLogsModel() {
        RaoRunnerLogsModel raoRunnerLogsModel = new RaoRunnerLogsModel("gridcapaTaskId", "computationId",
            "clientAppId", "level", "timestamp", "message", "serviceName");
        assertEquals("gridcapaTaskId", raoRunnerLogsModel.getGridcapaTaskId());
        assertEquals("computationId", raoRunnerLogsModel.getComputationId());
        assertEquals("clientAppId", raoRunnerLogsModel.getClientAppId());
        assertEquals("level", raoRunnerLogsModel.getLevel());
        assertEquals("timestamp", raoRunnerLogsModel.getTimestamp());
        assertEquals("message", raoRunnerLogsModel.getMessage());
        assertEquals("serviceName", raoRunnerLogsModel.getServiceName());
        assertEquals(Optional.empty(), raoRunnerLogsModel.getEventPrefix());
    }
}
