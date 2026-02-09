package com.farao_community.farao.swe_csa.app.security_evaluator.cnec_evaluator;

import com.farao_community.farao.swe_csa.app.security_evaluator.Border;
import com.farao_community.farao.swe_csa.app.security_evaluator.MultiBorderMonitoringInput;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;

public interface CnecEvaluator {
    Map<Border, MonitoringResult> evaluate(Network network, State state, Map<Border, Set<Cnec>> cnecsToEvaluatePerBorder);

    static CnecEvaluator getEvaluator(MultiBorderMonitoringInput multiBorderMonitoringInput, Logger businessLogger) {

        return switch (multiBorderMonitoringInput.getPhysicalParameter()) {
            case PhysicalParameter.FLOW -> new MarginEvaluator(multiBorderMonitoringInput, businessLogger);
            case PhysicalParameter.ANGLE, PhysicalParameter.VOLTAGE -> new MarginEvaluatorWithRA(multiBorderMonitoringInput, businessLogger);
        };
    }

}
