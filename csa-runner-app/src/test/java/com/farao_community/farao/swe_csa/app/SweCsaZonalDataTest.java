package com.farao_community.farao.swe_csa.app;

import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class SweCsaZonalDataTest {

    @Autowired
    FileImporter fileImporter;

    @Test
    void zonalDataCreationTest() {
        Network network = fileImporter.importNetwork(Objects.requireNonNull(getClass().getResource("/rao_inputs/network.xiidm")).toString());
        ZonalData<Scalable> zonalData = SweCsaZonalData.getZonalData(network);
        assertNotNull(zonalData);
        assertEquals("[10YFR-RTE------C]", zonalData.getDataPerZone().keySet().stream().sorted().toList().toString());

        List<Injection> injectionListFr = zonalData.getData("10YFR-RTE------C").filterInjections(network);
        assertEquals(5, injectionListFr.size());

    }
}

