/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.swe_csa.api.resource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Optional;

/**
 * @author mohamed ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Type("csa-response")
public class CsaResponse {
    @Id
    private final String id;
    private final Status status;
    private final String errorMessage; // only if status is REJECTED or ERROR, otherwise empty

    @JsonCreator
    public CsaResponse(@JsonProperty("id") String id, @JsonProperty("status") String status, @JsonProperty("error-message") String errorMessage) {
        this.id = id;
        this.status = Status.valueOf(status);
        this.errorMessage = errorMessage;
    }

    public String getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
