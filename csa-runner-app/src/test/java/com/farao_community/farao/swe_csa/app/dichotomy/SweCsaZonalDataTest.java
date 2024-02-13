package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.app.FileHelper;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class SweCsaZonalDataTest {

    @Autowired
    FileHelper fileHelper;

    @Test
    void zonalDataCreationTest() {
        Network network = fileHelper.importNetwork(Paths.get(new File(getClass().getResource("/TestCase_13_5_4.zip").getFile()).toString()));
        ZonalData<Scalable> zonalData = SweCsaZonalData.getZonalData(network);
        assertNotNull(zonalData);
        assertEquals("[BELGIUM, FRANCE, GERMANY, NETHERLANDS]", zonalData.getDataPerZone().keySet().stream().sorted().collect(Collectors.toList()).toString());

        List<Injection> injectionListBe = zonalData.getData("BELGIUM").filterInjections(network);
        assertEquals(4, injectionListBe.size());

        List<Injection> injectionListFr = zonalData.getData("FRANCE").filterInjections(network);
        assertEquals(5, injectionListFr.size());

        List<Injection> injectionListGe = zonalData.getData("GERMANY").filterInjections(network);
        assertEquals(4, injectionListGe.size());

        List<Injection> injectionListNl = zonalData.getData("NETHERLANDS").filterInjections(network);
        assertEquals(3, injectionListNl.size());
    }
}
