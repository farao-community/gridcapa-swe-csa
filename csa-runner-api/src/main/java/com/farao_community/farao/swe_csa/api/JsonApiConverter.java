/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.swe_csa.api;

import com.farao_community.farao.swe_csa.api.exception.AbstractCsaException;
import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.jasminb.jsonapi.JSONAPIDocument;
import com.github.jasminb.jsonapi.ResourceConverter;
import com.github.jasminb.jsonapi.SerializationFeature;
import com.github.jasminb.jsonapi.exceptions.DocumentSerializationException;
import com.github.jasminb.jsonapi.models.errors.Error;

/**
 * JSON API conversion component
 * Allows automatic conversion from resources or exceptions towards JSON API formatted bytes.
 *
 * @author mohamed ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class JsonApiConverter {
    private final ObjectMapper objectMapper;

    public JsonApiConverter() {
        objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();
    }

    public <T> T fromJsonMessage(byte[] jsonMessage, Class<T> tClass) {
        ResourceConverter converter = createConverter(tClass);
        try {
            return converter.readDocument(jsonMessage, tClass).get();
        } catch (Exception e) {
            throw new CsaInvalidDataException("unknown", String.format("Message cannot be converted to class %s", tClass.getName()), e);
        }

    }

    public <T> byte[] toJsonMessage(T jsonApiObject, Class<T> clazz) {
        ResourceConverter converter = createConverter(clazz);
        JSONAPIDocument<?> jsonApiDocument = new JSONAPIDocument<>(jsonApiObject);
        try {
            return converter.writeDocument(jsonApiDocument);
        } catch (DocumentSerializationException e) {
            throw new CsaInternalException("unknown", "Exception occurred during message conversion", e);
        }
    }

    public byte[] toJsonMessage(AbstractCsaException exception) {
        ResourceConverter converter = createConverter();
        JSONAPIDocument<?> jsonApiDocument = new JSONAPIDocument<>(convertExceptionToJsonError(exception));
        try {
            return converter.writeDocument(jsonApiDocument);
        } catch (DocumentSerializationException e) {
            throw new CsaInternalException("unknown", "Exception occurred during message conversion", e);
        }
    }

    private ResourceConverter createConverter(Class<?>... classes) {
        ResourceConverter converter = new ResourceConverter(objectMapper, classes);
        converter.disableSerializationOption(SerializationFeature.INCLUDE_META);
        return converter;
    }

    private Error convertExceptionToJsonError(AbstractCsaException exception) {
        Error error = new Error();
        error.setStatus(Integer.toString(exception.getStatus()));
        error.setCode(exception.getCode());
        error.setTitle(exception.getTitle());
        error.setDetail(exception.getDetails());
        error.setId(exception.getId());
        return error;
    }
}
