package com.farao_community.farao.swe_csa.api;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */
public class SweCsaLogsModel {

    private final String taskId;
    private final String raoRunId;
    private final String level;
    private final String timestamp;
    private final String message;

    @JsonCreator
    public SweCsaLogsModel(@JsonProperty("taskId") String taskId, @JsonProperty("raoRunId") String raoRunId, @JsonProperty("level") String level, @JsonProperty("timestamp") String timestamp, @JsonProperty("message") String message) {
        this.taskId = taskId;
        this.raoRunId = raoRunId;
        this.level = level;
        this.timestamp = timestamp;
        this.message = message;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getRaoRunId() {
        return raoRunId;
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
}

