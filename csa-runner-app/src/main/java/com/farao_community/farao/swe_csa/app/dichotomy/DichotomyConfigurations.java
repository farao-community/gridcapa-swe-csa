package com.farao_community.farao.swe_csa.app.dichotomy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties("dichotomy-parameters")
public class DichotomyConfigurations {
    private int precision;
    private int maxDichotomiesForPtEsBorder;
    private int maxDichotomiesForFrEsBorder;
    private double maxCtRaPtEs;
    private double maxCtRaFrEs;

    public int getPrecision() {
        return precision;
    }

    public int getMaxDichotomiesForPtEsBorder() {
        return maxDichotomiesForPtEsBorder;
    }

    public int getMaxDichotomiesForFrEsBorder() {
        return maxDichotomiesForFrEsBorder;
    }

    public double getMaxCtRaPtEs() {
        return maxCtRaPtEs;
    }

    public double getMaxCtRaFrEs() {
        return maxCtRaFrEs;
    }
}
