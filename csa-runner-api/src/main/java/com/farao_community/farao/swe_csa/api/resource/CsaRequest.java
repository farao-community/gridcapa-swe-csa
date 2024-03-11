package com.farao_community.farao.swe_csa.api.resource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import org.apache.commons.lang3.builder.ToStringBuilder;

@Type("csa-rao-request")
public class CsaRequest {
    @Id
    private final String id;
    private String businessTimestamp;
    private String gridModelUri;
    private String cracFileUri;
    private String resultsUri;

    @JsonCreator
    public CsaRequest(@JsonProperty("id") String id,
                      @JsonProperty("businessTimestamp") String businessTimestamp,
                      @JsonProperty("gridModelUri") String gridModelUri,
                      @JsonProperty("cracFileUri") String cracFileUri,
                      @JsonProperty("resultsUri") String resultsUri) {
        this.id = id;
        this.businessTimestamp = businessTimestamp;
        this.gridModelUri = gridModelUri;
        this.cracFileUri = cracFileUri;
        this.resultsUri = resultsUri;
    }

    public String getId() {
        return id;
    }

    public String getBusinessTimestamp() {
        return businessTimestamp;
    }

    public void setBusinessTimestamp(String businessTimestamp) {
        this.businessTimestamp = businessTimestamp;
    }

    public String getGridModelUri() {
        return gridModelUri;
    }

    public void setGridModelUri(String gridModelUri) {
        this.gridModelUri = gridModelUri;
    }

    public String getCracFileUri() {
        return cracFileUri;
    }

    public void setCracFileUri(String cracFileUri) {
        this.cracFileUri = cracFileUri;
    }

    public String getResultsUri() {
        return resultsUri;
    }

    public void setResultsUri(String resultsUri) {
        this.resultsUri = resultsUri;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
