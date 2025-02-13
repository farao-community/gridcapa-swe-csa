/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.swe_csa.api.exception;

/**
 * @author mohamed ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class CsaInvalidDataException extends AbstractCsaException {

    private static final int STATUS = 400;
    private static final String CODE = "400-InvalidDataException";

    public CsaInvalidDataException(String id, String message) {
        super(id, message);
    }

    public CsaInvalidDataException(String id, String message, Throwable throwable) {
        super(id, message, throwable);
    }

    @Override
    public int getStatus() {
        return STATUS;
    }

    @Override
    public String getCode() {
        return CODE;
    }
}
