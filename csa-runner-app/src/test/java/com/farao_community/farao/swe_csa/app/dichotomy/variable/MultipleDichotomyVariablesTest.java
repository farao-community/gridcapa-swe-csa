package com.farao_community.farao.swe_csa.app.dichotomy.variable;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

import com.farao_community.farao.dichotomy.api.exceptions.DichotomyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultipleDichotomyVariablesTest {

    MultipleDichotomyVariables mdv1, mdv2, mdv3, mdv4, mdv5, mdv6, mdv7;
    @BeforeEach
    void setup() {
        mdv1 = new MultipleDichotomyVariables(Map.of("k1", 1., "k2", 0.1));
        mdv2 = new MultipleDichotomyVariables(Map.of("k1", 5., "k2", 5.));
        mdv3 = new MultipleDichotomyVariables(Map.of("k1", 7., "k2", 3.));
        mdv4 = new MultipleDichotomyVariables(Map.of("k3", 7., "k2", 3.));
        mdv5 = new MultipleDichotomyVariables(Map.of("k1", 7., "k2", 3., "k3", 3.));
        mdv6 = new MultipleDichotomyVariables(new HashMap<>());
        mdv7 = new MultipleDichotomyVariables(new HashMap<>());

    }

    @Test
    void testValues() {
        assertEquals(1., mdv1.values().get("k1"));
        assertEquals(5., mdv2.values().get("k2"));
        assertNull(mdv1.values().get("k3"));
    }
    @Test
    void testIsGreaterThan() {
        assertTrue(mdv2.isGreaterThan(mdv1));
        assertFalse(mdv1.isGreaterThan(mdv2));
        assertTrue(mdv2.isGreaterThan(mdv3));
        assertTrue(mdv3.isGreaterThan(mdv2));
        assertFalse(mdv6.isGreaterThan(mdv7));
        assertThrows(DichotomyException.class, () -> mdv1.isGreaterThan(mdv4));
        assertThrows(DichotomyException.class, () -> mdv1.isGreaterThan(mdv5));

    }

    @Test
    void testDistanceTo() {
        assertEquals(4.9, mdv1.distanceTo(mdv2));
        assertEquals(4.9, mdv2.distanceTo(mdv1));
        assertEquals(6., mdv1.distanceTo(mdv3));
        assertEquals(0., mdv6.distanceTo(mdv7));
        assertThrows(DichotomyException.class, () -> mdv1.distanceTo(mdv4));
        assertThrows(DichotomyException.class, () -> mdv1.distanceTo(mdv5));
    }

    @Test
    void testHalfRangeWith() {
        assertEquals(0., new MultipleDichotomyVariables(Map.of("k1", 3., "k2", 2.55)).distanceTo(mdv1.halfRangeWith(mdv2)));
        assertEquals(0., new MultipleDichotomyVariables(Map.of("k1", 3., "k2", 2.55)).distanceTo(mdv2.halfRangeWith(mdv1)));
        assertEquals(0., new MultipleDichotomyVariables(Map.of("k1", 4., "k2", 1.55)).distanceTo(mdv1.halfRangeWith(mdv3)));
        assertEquals(0., new MultipleDichotomyVariables(new HashMap<>()).distanceTo(mdv6.halfRangeWith(mdv7)));
        assertThrows(DichotomyException.class, () -> mdv1.halfRangeWith(mdv4));
        assertThrows(DichotomyException.class, () -> mdv1.halfRangeWith(mdv5));
    }

    @Test
    void testPrint() {
        assertEquals("k1 : 1, k2 : 0", mdv1.print());
        assertEquals("", mdv6.print());
    }
}
