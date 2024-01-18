package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.powsybl.iidm.network.Network;

public interface NetworkShifter<U extends DichotomyVariable<U>> {

    void shiftNetwork(U stepValue, Network network) throws GlskLimitationException, ShiftingException;
}
