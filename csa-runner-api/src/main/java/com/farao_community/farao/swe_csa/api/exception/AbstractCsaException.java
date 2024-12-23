/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.swe_csa.api.exception;

/**
 * Custom abstract exception to be extended by all application exceptions.
 * Any subclass may be automatically wrapped to a JSON API error message if needed
 *
 * @author mohamed ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public abstract class AbstractCsaException extends RuntimeException {

    private final String id;

    protected AbstractCsaException(String id, String message) {
        super(message);
        this.id = id;
    }

    protected AbstractCsaException(String id, String message, Throwable throwable) {
        super(message, throwable);
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public abstract int getStatus();

    public abstract String getCode();

    public final String getTitle() {
        return getMessage();
    }

    public final String getDetails() {
        String message = getMessage();
        if (message != null) {
            StringBuilder sb = new StringBuilder(message);
            Throwable cause = getCause();
            while (cause != null) {
                sb.append("; nested exception is ").append(cause);
                cause = cause.getCause();
            }
            return sb.toString();
        } else {
            return "No details";
        }
    }
}
