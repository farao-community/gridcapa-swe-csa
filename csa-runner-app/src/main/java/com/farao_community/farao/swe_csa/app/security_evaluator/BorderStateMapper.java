package com.farao_community.farao.swe_csa.app.security_evaluator;

import com.farao_community.farao.swe_csa.app.security_evaluator.MultiBorderMonitoringInput.CracRaoResultPair;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public final class BorderStateMapper {

    private BorderStateMapper() {
    }

    /**
     * Builds a map of State → set of Borders that contain that state.
     * Preventive states are excluded.
     */
    public static Map<State, EnumSet<Border>> mapContingencyStates(MultiBorderMonitoringInput monitoringInput) {
        Map<State, EnumSet<Border>> merged = new HashMap<>();

        for (Border border : monitoringInput.getBorders()) {
            CracRaoResultPair input = monitoringInput.getCracRaoResultPair(border);
            input.crac().getCnecs(monitoringInput.getPhysicalParameter()).stream()
                    .map(Cnec::getState)
                    .filter(state -> !state.isPreventive())
                    .forEach(state -> merged.computeIfAbsent(state, k -> EnumSet.noneOf(Border.class)).add(border));
        }

        return merged;
    }
}
