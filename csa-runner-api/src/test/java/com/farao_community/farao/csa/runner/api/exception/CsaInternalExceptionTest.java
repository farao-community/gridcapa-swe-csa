/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.csa.runner.api.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mohamed.ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class CsaInternalExceptionTest {

    @Test
    void checkException() {
        AbstractCsaException csaException = new CsaInternalException("Exception message");
        assertEquals("Exception message", csaException.getMessage());
        assertEquals(500, csaException.getStatus());

        Exception cause = new RuntimeException("Cause");
        AbstractCsaException exception = new CsaInternalException("Exception message", cause);
        assertEquals("Exception message", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
