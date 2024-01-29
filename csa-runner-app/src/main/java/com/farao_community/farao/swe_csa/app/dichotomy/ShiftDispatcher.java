package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;

import java.util.Map;

public interface ShiftDispatcher<U extends DichotomyVariable<U>> {
    Map<String, Double> dispatch(U var1) throws ShiftingException;
}
