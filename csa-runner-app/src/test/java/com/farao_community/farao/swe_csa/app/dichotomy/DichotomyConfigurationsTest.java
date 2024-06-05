package com.farao_community.farao.swe_csa.app.dichotomy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class DichotomyConfigurationsTest {

    @Autowired
    DichotomyConfigurations dichotomyConfigurations;

    @Test
    void checkDichotomyConfigurations() {
        Assertions.assertEquals(10, dichotomyConfigurations.getPrecision());
        Assertions.assertEquals(10, dichotomyConfigurations.getMaxDichotomiesForPtEsBorder());
        Assertions.assertEquals(10, dichotomyConfigurations.getMaxDichotomiesForFrEsBorder());
        Assertions.assertEquals(50000, dichotomyConfigurations.getMaxCtRaPtEs());
        Assertions.assertEquals(50000, dichotomyConfigurations.getMaxCtRaFrEs());
    }
}
