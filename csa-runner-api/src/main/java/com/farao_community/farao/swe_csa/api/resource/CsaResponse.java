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

/**
 * @author mohamed ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Type("csa-rao-response")
public class CsaResponse {
    @Id
    private final String id;
    private final Status ptEsStatus;
    private final String ptEsRaoResultUri;
    private final Status frEsStatus;
    private final String frEsRaoResultUri;

    @JsonCreator
    public CsaResponse(@JsonProperty("id") String id, @JsonProperty("ptEsStatus") String ptEsStatus, @JsonProperty("ptEsRaoResultUri") String ptEsRaoResultUri, @JsonProperty("frEsStatus") String frEsStatus, @JsonProperty("frEsRaoResultUri") String frEsRaoResultUri) {
        this.id = id;
        this.ptEsStatus = Status.valueOf(ptEsStatus);
        this.ptEsRaoResultUri = ptEsRaoResultUri;
        this.frEsStatus = Status.valueOf(frEsStatus);
        this.frEsRaoResultUri = frEsRaoResultUri;
    }

    public String getId() {
        return id;
    }

    public Status getPtEsStatus() {
        return ptEsStatus;
    }

    public String getPtEsRaoResultUri() {
        return ptEsRaoResultUri;
    }

    public Status getFrEsStatus() {
        return frEsStatus;
    }

    public String getFrEsRaoResultUri() {
        return frEsRaoResultUri;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
