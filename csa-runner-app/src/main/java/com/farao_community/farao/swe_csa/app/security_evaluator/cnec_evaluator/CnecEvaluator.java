package com.farao_community.farao.swe_csa.app.security_evaluator.cnec_evaluator;

import com.farao_community.farao.swe_csa.app.security_evaluator.Border;
import com.farao_community.farao.swe_csa.app.security_evaluator.ParallelRaoMonitoringInput;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;

public interface CnecEvaluator {
    Map<Border, MonitoringResult> evaluate(State state, Map<Border, Set<Cnec>> impactedCnecMap, Network network);

    static CnecEvaluator getEvaluator( ParallelRaoMonitoringInput parallelInput, Logger businessLogger, String loadFlowProvider, LoadFlowParameters loadFlowParameters) {

        return switch (parallelInput.getPhysicalParameter()) {
            case PhysicalParameter.FLOW -> new MarginEvaluator(parallelInput, businessLogger, loadFlowProvider, loadFlowParameters);
            case PhysicalParameter.ANGLE, PhysicalParameter.VOLTAGE -> new MarginEvaluatorWithRA(parallelInput, businessLogger, loadFlowProvider, loadFlowParameters);
        };
    }

}
