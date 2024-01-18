package com.farao_community.farao.swe_csa.api.resource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;

@Type("csa-interruption-request")
public class InterruptionRequest {

    @Id
    private final String id;

    @JsonCreator
    public InterruptionRequest(@JsonProperty("id") String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
