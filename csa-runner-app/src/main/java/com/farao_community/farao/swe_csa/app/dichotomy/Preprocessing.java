package com.farao_community.farao.swe_csa.app.dichotomy;

import com.powsybl.iidm.network.Country;
import com.powsybl.openrao.data.crac.api.Crac;

public final class Preprocessing {

    private Preprocessing() {
    }

    private static final String CT_RA_PTES = "CT_RA_PTES";
    private static final String CT_RA_FRES = "CT_RA_FRES";
    private static final String CT_RA_ESPT = "CT_RA_ESPT";
    private static final String CT_RA_ESFR = "CT_RA_ESFR";

    static void updateCracWithPtEsCounterTradeRangeActions(Crac crac) {
        crac.newCounterTradeRangeAction()
            .withId(CT_RA_PTES)
            .withOperator("REN")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.PT)
            .withImportingCountry(Country.ES)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CT_RA_ESPT)
            .withOperator("REE")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.ES)
            .withImportingCountry(Country.PT)
            .add();
    }

    static void updateCracWithFrEsCounterTradeRangeActions(Crac crac) {
        crac.newCounterTradeRangeAction()
            .withId(CT_RA_ESFR)
            .withOperator("REE")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.ES)
            .withImportingCountry(Country.FR)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CT_RA_FRES)
            .withOperator("RTE")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.FR)
            .withImportingCountry(Country.ES)
            .add();
    }
}
