package com.farao_community.farao.swe_csa.app.dichotomy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DichotomyConfigurations {

    @Value("${dichotomy-parameters.precision}")
    private Integer precision;

    @Value("${dichotomy-parameters.max-iterations-for-pt-es-border}")
    private Integer maxDichotomiesForPtEsBorder;

    @Value("${dichotomy-parameters.max-iterations-for-fr-es-border}")
    private Integer maxDichotomiesForFrEsBorder;

    @Value("${dichotomy-parameters.max-ct-ra-pt-es}")
    private Double maxCtRaPtEs;

    @Value("${dichotomy-parameters.max-ct-ra-fr-es}")
    private Double maxCtRaFrEs;

    public Integer getPrecision() {
        return precision;
    }

    public Integer getMaxDichotomiesForPtEsBorder() {
        return maxDichotomiesForPtEsBorder;
    }

    public Integer getMaxDichotomiesForFrEsBorder() {
        return maxDichotomiesForFrEsBorder;
    }

    public Double getMaxCtRaPtEs() {
        return maxCtRaPtEs;
    }

    public Double getMaxCtRaFrEs() {
        return maxCtRaFrEs;
    }
}
