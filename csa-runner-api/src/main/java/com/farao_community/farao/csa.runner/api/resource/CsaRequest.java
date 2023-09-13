package com.farao_community.farao.csa.runner.api.resource;

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
    private CommonProfiles commonProfiles;
    private Profiles frProfiles;
    private Profiles esProfiles;
    private Profiles ptProfiles;
    private String resultsUri;

    @JsonCreator
    public CsaRequest(@JsonProperty("id") String id,
                      @JsonProperty("businessTimestamp") String businessTimestamp,
                      @JsonProperty("commonProfiles") CommonProfiles commonProfiles,
                      @JsonProperty("frProfiles") Profiles frProfiles,
                      @JsonProperty("esProfiles") Profiles esProfiles,
                      @JsonProperty("ptProfiles") Profiles ptProfiles,
                      @JsonProperty("resultsUri") String resultsUri) {
        this.id = id;
        this.businessTimestamp = businessTimestamp;
        this.commonProfiles = commonProfiles;
        this.frProfiles = frProfiles;
        this.esProfiles = esProfiles;
        this.ptProfiles = ptProfiles;
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

    public CommonProfiles getCommonProfiles() {
        return commonProfiles;
    }

    public void setCommonProfiles(CommonProfiles commonProfiles) {
        this.commonProfiles = commonProfiles;
    }

    public Profiles getFrProfiles() {
        return frProfiles;
    }

    public void setFrProfiles(Profiles frProfiles) {
        this.frProfiles = frProfiles;
    }

    public Profiles getEsProfiles() {
        return esProfiles;
    }

    public void setEsProfiles(Profiles esProfiles) {
        this.esProfiles = esProfiles;
    }

    public Profiles getPtProfiles() {
        return ptProfiles;
    }

    public void setPtProfiles(Profiles ptProfiles) {
        this.ptProfiles = ptProfiles;
    }

    public String getResultsUri() {
        return resultsUri;
    }

    public void setResultsUri(String resultsUri) {
        this.resultsUri = resultsUri;
    }

    public static class CommonProfiles {
        private String tpbdProfileUri;
        private String eqbdProfileUri;
        private String svProfileUri;

        public String getTpbdProfileUri() {
            return tpbdProfileUri;
        }

        public void setTpbdProfileUri(String tpbdProfileUri) {
            this.tpbdProfileUri = tpbdProfileUri;
        }

        public String getEqbdProfileUri() {
            return eqbdProfileUri;
        }

        public void setEqbdProfileUri(String eqbdProfileUri) {
            this.eqbdProfileUri = eqbdProfileUri;
        }

        public String getSvProfileUri() {
            return svProfileUri;
        }

        public void setSvProfileUri(String svProfileUri) {
            this.svProfileUri = svProfileUri;
        }
    }

    public static class Profiles {
        private String sshProfileUri;

        private String tpProfileUri;

        private String eqProfileUri;

        private String aeProfileUri;

        private String coProfileUri;

        private String raProfileUri;

        private String erProfileUri;

        private String ssiProfileUri;

        private String sisProfileUri;

        private String maProfileUri;

        private String smProfileUri;

        private String asProfileUri;

        public String getSshProfileUri() {
            return sshProfileUri;
        }

        public void setSshProfileUri(String sshProfileUri) {
            this.sshProfileUri = sshProfileUri;
        }

        public String getTpProfileUri() {
            return tpProfileUri;
        }

        public void setTpProfileUri(String tpProfileUri) {
            this.tpProfileUri = tpProfileUri;
        }

        public String getEqProfileUri() {
            return eqProfileUri;
        }

        public void setEqProfileUri(String eqProfileUri) {
            this.eqProfileUri = eqProfileUri;
        }

        public String getAeProfileUri() {
            return aeProfileUri;
        }

        public void setAeProfileUri(String aeProfileUri) {
            this.aeProfileUri = aeProfileUri;
        }

        public String getCoProfileUri() {
            return coProfileUri;
        }

        public void setCoProfileUri(String coProfileUri) {
            this.coProfileUri = coProfileUri;
        }

        public String getRaProfileUri() {
            return raProfileUri;
        }

        public void setRaProfileUri(String raProfileUri) {
            this.raProfileUri = raProfileUri;
        }

        public String getErProfileUri() {
            return erProfileUri;
        }

        public void setErProfileUri(String erProfileUri) {
            this.erProfileUri = erProfileUri;
        }

        public String getSsiProfileUri() {
            return ssiProfileUri;
        }

        public void setSsiProfileUri(String ssiProfileUri) {
            this.ssiProfileUri = ssiProfileUri;
        }

        public String getSisProfileUri() {
            return sisProfileUri;
        }

        public void setSisProfileUri(String sisProfileUri) {
            this.sisProfileUri = sisProfileUri;
        }

        public String getMaProfileUri() {
            return maProfileUri;
        }

        public void setMaProfileUri(String maProfileUri) {
            this.maProfileUri = maProfileUri;
        }

        public String getSmProfileUri() {
            return smProfileUri;
        }

        public void setSmProfileUri(String smProfileUri) {
            this.smProfileUri = smProfileUri;
        }

        public String getAsProfileUri() {
            return asProfileUri;
        }

        public void setAsProfileUri(String asProfileUri) {
            this.asProfileUri = asProfileUri;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
