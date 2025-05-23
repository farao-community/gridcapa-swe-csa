package com.farao_community.farao.swe_csa.app.dichotomy;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

class PreprocessingTest {

    @Test
    void testUpdateCracWithPtEsCounterTradeRangeActions() throws IOException {
        Network network = Network.read("network.xiidm", getClass().getResourceAsStream("/rao_inputs/network.xiidm"));
        Crac crac = Crac.read("crac.json", Objects.requireNonNull(getClass().getResourceAsStream("/rao_inputs/crac.json")), network);
        Preprocessing.updateCracWithPtEsCounterTradeRangeActions(crac);
        Assertions.assertNotNull(crac.getCounterTradeRangeAction("CT_RA_PTES"));
        Assertions.assertEquals("REN", crac.getCounterTradeRangeAction("CT_RA_PTES").getOperator());
        Assertions.assertEquals(-50000., crac.getCounterTradeRangeAction("CT_RA_PTES").getRanges().get(0).getMin());
        Assertions.assertEquals(50000., crac.getCounterTradeRangeAction("CT_RA_PTES").getRanges().get(0).getMax());
        Assertions.assertEquals(Country.PT, crac.getCounterTradeRangeAction("CT_RA_PTES").getExportingCountry());
        Assertions.assertEquals(Country.ES, crac.getCounterTradeRangeAction("CT_RA_PTES").getImportingCountry());

        Assertions.assertNotNull(crac.getCounterTradeRangeAction("CT_RA_ESPT"));
        Assertions.assertEquals("REE", crac.getCounterTradeRangeAction("CT_RA_ESPT").getOperator());
        Assertions.assertEquals(-50000., crac.getCounterTradeRangeAction("CT_RA_ESPT").getRanges().get(0).getMin());
        Assertions.assertEquals(50000., crac.getCounterTradeRangeAction("CT_RA_ESPT").getRanges().get(0).getMax());
        Assertions.assertEquals(Country.ES, crac.getCounterTradeRangeAction("CT_RA_ESPT").getExportingCountry());
        Assertions.assertEquals(Country.PT, crac.getCounterTradeRangeAction("CT_RA_ESPT").getImportingCountry());
    }

    @Test
    void testUpdateCracWithFrEsCounterTradeRangeActions() throws IOException {
        Network network = Network.read("network.xiidm", getClass().getResourceAsStream("/rao_inputs/network.xiidm"));
        Crac crac = Crac.read("crac.json", Objects.requireNonNull(getClass().getResourceAsStream("/rao_inputs/crac.json")), network);
        Preprocessing.updateCracWithFrEsCounterTradeRangeActions(crac);
        Assertions.assertNotNull(crac.getCounterTradeRangeAction("CT_RA_FRES"));
        Assertions.assertEquals("RTE", crac.getCounterTradeRangeAction("CT_RA_FRES").getOperator());
        Assertions.assertEquals(-50000., crac.getCounterTradeRangeAction("CT_RA_FRES").getRanges().get(0).getMin());
        Assertions.assertEquals(50000., crac.getCounterTradeRangeAction("CT_RA_FRES").getRanges().get(0).getMax());
        Assertions.assertEquals(Country.FR, crac.getCounterTradeRangeAction("CT_RA_FRES").getExportingCountry());
        Assertions.assertEquals(Country.ES, crac.getCounterTradeRangeAction("CT_RA_FRES").getImportingCountry());

        Assertions.assertNotNull(crac.getCounterTradeRangeAction("CT_RA_ESFR"));
        Assertions.assertEquals("REE", crac.getCounterTradeRangeAction("CT_RA_ESFR").getOperator());
        Assertions.assertEquals(-50000., crac.getCounterTradeRangeAction("CT_RA_ESFR").getRanges().get(0).getMin());
        Assertions.assertEquals(50000., crac.getCounterTradeRangeAction("CT_RA_ESFR").getRanges().get(0).getMax());
        Assertions.assertEquals(Country.ES, crac.getCounterTradeRangeAction("CT_RA_ESFR").getExportingCountry());
        Assertions.assertEquals(Country.FR, crac.getCounterTradeRangeAction("CT_RA_ESFR").getImportingCountry());
    }
}
