/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.swe_csa.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */
public class SweCsaLogsModelTest {

    @Test
    void testSweCsaLogsModel() {
        SweCsaLogsModel sweCsaLogsModel = new SweCsaLogsModel("taskId", "level", "timestamp", "message");
        assertEquals("taskId", sweCsaLogsModel.getTaskId());
        assertEquals(null, sweCsaLogsModel.getRaoRunId());
        assertEquals("level", sweCsaLogsModel.getLevel());
        assertEquals("timestamp", sweCsaLogsModel.getTimestamp());
        assertEquals("message", sweCsaLogsModel.getMessage());
    }
}
