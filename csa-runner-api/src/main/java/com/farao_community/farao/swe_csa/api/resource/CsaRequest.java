package com.farao_community.farao.swe_csa.api.resource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Objects;

@Type("csa-rao-request")
public class CsaRequest {
    @Id
    private final String id;
    private String businessTimestamp;
    private String gridModelUri;
    private String glskUri;
    private String ptEsCracFileUri;
    private String frEsCracFileUri;

    @JsonCreator
    public CsaRequest(@JsonProperty("id") String id,
                      @JsonProperty("businessTimestamp") String businessTimestamp,
                      @JsonProperty("gridModelUri") String gridModelUri,
                      @JsonProperty("glskUri") String glskUri,
                      @JsonProperty("ptEsCracFileUri") String ptEsCracFileUri,
                      @JsonProperty("frEsCracFileUri") String frEsCracFileUri) {
        this.id = id;
        this.businessTimestamp = businessTimestamp;
        this.gridModelUri = gridModelUri;
        this.glskUri = glskUri;
        this.ptEsCracFileUri = ptEsCracFileUri;
        this.frEsCracFileUri = frEsCracFileUri;

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

    public String getPtEsCracFileUri() {
        return ptEsCracFileUri;
    }

    public String getFrEsCracFileUri() {
        return frEsCracFileUri;
    }

    public void setPtEsCracFileUri(String ptEsCracFileUri) {
        this.ptEsCracFileUri = ptEsCracFileUri;
    }

    public void setFrEsCracFileUri(String frEsCracFileUri) {
        this.frEsCracFileUri = frEsCracFileUri;
    }

    public String getGlskUri() {
        return glskUri;
    }

    public void setGlskUri(String glskUri) {
        this.glskUri = glskUri;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        CsaRequest other = (CsaRequest) obj;
        return this.id.equals(other.id) && this.businessTimestamp.equals(other.businessTimestamp) && this.gridModelUri.equals(other.gridModelUri) && this.glskUri.equals(other.glskUri) && this.ptEsCracFileUri.equals(other.ptEsCracFileUri) && this.frEsCracFileUri.equals(other.frEsCracFileUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, businessTimestamp, gridModelUri, glskUri, ptEsCracFileUri, frEsCracFileUri);
    }
}
