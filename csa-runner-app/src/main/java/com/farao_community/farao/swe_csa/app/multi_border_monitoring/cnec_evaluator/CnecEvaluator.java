package com.farao_community.farao.swe_csa.app.multi_border_monitoring.cnec_evaluator;

import com.farao_community.farao.swe_csa.app.multi_border_monitoring.Border;
import com.farao_community.farao.swe_csa.app.multi_border_monitoring.MultiBorderMonitoringInput;
import com.farao_community.farao.swe_csa.app.multi_border_monitoring.MultiBorderMonitoringResult;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;

public interface CnecEvaluator {
    MultiBorderMonitoringResult evaluate(Network network, State state, Map<Border, Set<Cnec>> cnecsToEvaluatePerBorder);

    static CnecEvaluator getEvaluator(MultiBorderMonitoringInput multiBorderMonitoringInput, Logger businessLogger) {

        return switch (multiBorderMonitoringInput.getPhysicalParameter()) {
            case PhysicalParameter.FLOW -> new MarginEvaluator(multiBorderMonitoringInput, businessLogger);
            case PhysicalParameter.ANGLE, PhysicalParameter.VOLTAGE -> new MarginEvaluatorWithRA(multiBorderMonitoringInput, businessLogger);
        };
    }

}
