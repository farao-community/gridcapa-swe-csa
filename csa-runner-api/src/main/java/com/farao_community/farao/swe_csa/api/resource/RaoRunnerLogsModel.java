/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.swe_csa.api.resource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class RaoRunnerLogsModel {

    private final String gridcapaTaskId;
    private final String computationId;
    private final String clientAppId;
    private final String level;
    private final String timestamp;
    private final String message;
    private final String serviceName;
    private final String eventPrefix;

    @JsonCreator
    public RaoRunnerLogsModel(@JsonProperty("gridcapaTaskId") String gridcapaTaskId, @JsonProperty("computationId") String computationId, @JsonProperty("clientAppId") String clientAppId,
                              @JsonProperty("level") String level, @JsonProperty("timestamp") String timestamp, @JsonProperty("message") String message, @JsonProperty("serviceName") String serviceName, @JsonProperty("eventPrefix") String eventPrefix) {
        this.gridcapaTaskId = gridcapaTaskId;
        this.computationId = computationId;
        this.clientAppId = clientAppId;
        this.level = level;
        this.timestamp = timestamp;
        this.message = message;
        this.serviceName = serviceName;
        this.eventPrefix = eventPrefix;
    }

    public RaoRunnerLogsModel(@JsonProperty("gridcapaTaskId") String gridcapaTaskId, @JsonProperty("computationId") String computationId, @JsonProperty("clientAppId") String clientAppId,
                              @JsonProperty("level") String level, @JsonProperty("timestamp") String timestamp, @JsonProperty("message") String message, @JsonProperty("serviceName") String serviceName) {
        this(gridcapaTaskId, computationId, clientAppId, level, timestamp, message, serviceName, null);
    }

    public String getGridcapaTaskId() {
        return gridcapaTaskId;
    }

    public String getComputationId() {
        return computationId;
    }

    public String getClientAppId() {
        return clientAppId;
    }

    public String getLevel() {
        return level;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getEventPrefix() {
        return eventPrefix;
    }
}
