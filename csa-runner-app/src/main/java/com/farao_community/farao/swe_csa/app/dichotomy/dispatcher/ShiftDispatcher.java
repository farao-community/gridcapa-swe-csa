package com.farao_community.farao.swe_csa.app.dichotomy.dispatcher;

import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.DichotomyVariable;

import java.util.Map;

public interface ShiftDispatcher<U extends DichotomyVariable<U>> {
    Map<String, Double> dispatch(U var1) throws ShiftingException;
}
