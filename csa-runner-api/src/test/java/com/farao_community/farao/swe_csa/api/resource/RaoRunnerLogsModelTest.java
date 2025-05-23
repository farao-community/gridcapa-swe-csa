/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.swe_csa.api.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

class RaoRunnerLogsModelTest {

    @Test
    void testConstructorAndGetters() {
        String gridcapaTaskId = "task1";
        String computationId = "comp1";
        String clientAppId = "app1";
        String level = "INFO";
        String timestamp = "2025-05-23T10:39:48";
        String message = "Test message";
        String serviceName = "TestService";
        String eventPrefix = "event1";

        RaoRunnerLogsModel model = new RaoRunnerLogsModel(gridcapaTaskId, computationId, clientAppId, level, timestamp, message, serviceName, eventPrefix);

        assertEquals(gridcapaTaskId, model.getGridcapaTaskId());
        assertEquals(computationId, model.getComputationId());
        assertEquals(clientAppId, model.getClientAppId());
        assertEquals(level, model.getLevel());
        assertEquals(timestamp, model.getTimestamp());
        assertEquals(message, model.getMessage());
        assertEquals(serviceName, model.getServiceName());
        assertTrue(model.getEventPrefix().isPresent());
        assertEquals(eventPrefix, model.getEventPrefix().get());
    }

    @Test
    void testConstructorWithoutEventPrefix() {
        String gridcapaTaskId = "task2";
        String computationId = "comp2";
        String clientAppId = "app2";
        String level = "ERROR";
        String timestamp = "2025-05-23T10:40:00";
        String message = "Another test message";
        String serviceName = "AnotherService";

        RaoRunnerLogsModel model = new RaoRunnerLogsModel(gridcapaTaskId, computationId, clientAppId, level, timestamp, message, serviceName);

        assertEquals(gridcapaTaskId, model.getGridcapaTaskId());
        assertEquals(computationId, model.getComputationId());
        assertEquals(clientAppId, model.getClientAppId());
        assertEquals(level, model.getLevel());
        assertEquals(timestamp, model.getTimestamp());
        assertEquals(message, model.getMessage());
        assertEquals(serviceName, model.getServiceName());
        assertFalse(model.getEventPrefix().isPresent());
    }

    @Test
    void testToString() {
        String gridcapaTaskId = "task3";
        String computationId = "comp3";
        String clientAppId = "app3";
        String level = "DEBUG";
        String timestamp = "2025-05-23T10:41:00";
        String message = "Debugging message";
        String serviceName = "DebugService";
        String eventPrefix = "debugEvent";

        RaoRunnerLogsModel model = new RaoRunnerLogsModel(gridcapaTaskId, computationId, clientAppId, level, timestamp, message, serviceName, eventPrefix);

        assertNotNull(model.toString());
    }
}

