package com.farao_community.farao.swe_csa.app.rao_result;

import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.AngleCnec;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.crac.api.cnec.VoltageCnec;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.impl.AngleCnecValue;
import com.powsybl.openrao.data.crac.impl.VoltageCnecValue;
import com.powsybl.openrao.data.raoresult.api.ComputationStatus;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.raoresult.api.RaoResultClone;
import com.powsybl.openrao.monitoring.results.CnecResult;
import com.powsybl.openrao.monitoring.results.MonitoringResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Combines angle and voltage monitoring results on top of a base RaoResult in a single wrapper.
 * OpenRAO's RaoResultWithAngleMonitoring and RaoResultWithVoltageMonitoring only override the getters for
 * the physical parameter of their own. Every other method they don't override falls back to RaoResult's
 * throwing default implementation instead of being delegated to the wrapped result. This causes exception
 * during serialization of the RaoResult here after monitoring.
 */
public class RaoResultWithAngleAndVoltageMonitoring extends RaoResultClone {

    private final RaoResult raoResult;
    private final MonitoringResult angleMonitoringResult;
    private final MonitoringResult voltageMonitoringResult;

    public RaoResultWithAngleAndVoltageMonitoring(RaoResult raoResult, MonitoringResult angleMonitoringResult, MonitoringResult voltageMonitoringResult) {
        super(raoResult);
        this.raoResult = raoResult;
        this.angleMonitoringResult = angleMonitoringResult;
        this.voltageMonitoringResult = voltageMonitoringResult;
    }

    @Override
    public String getExecutionDetails() {
        StringBuilder details = new StringBuilder(raoResult.getExecutionDetails());
        if (angleMonitoringResult != null) {
            details.append(" and went through angle monitoring");
        }
        if (voltageMonitoringResult != null) {
            details.append(" and went through voltage monitoring");
        }
        return details.toString();
    }

    @Override
    public ComputationStatus getComputationStatus() {
        boolean angleFailed = angleMonitoringResult != null && angleMonitoringResult.getStatus().equals(Cnec.SecurityStatus.FAILURE);
        boolean voltageFailed = voltageMonitoringResult != null && voltageMonitoringResult.getStatus().equals(Cnec.SecurityStatus.FAILURE);
        if (angleFailed || voltageFailed) {
            return ComputationStatus.FAILURE;
        }
        return raoResult.getComputationStatus();
    }

    @Override
    public double getAngle(Instant optimizedInstant, AngleCnec angleCnec, Unit unit) {
        if (angleMonitoringResult == null) {
            return raoResult.getAngle(optimizedInstant, angleCnec, unit);
        }
        unit.checkPhysicalParameter(PhysicalParameter.ANGLE);
        return getAngleCnecResult(optimizedInstant, angleCnec)
                .map(r -> ((AngleCnecValue) r.getValue()).value())
                .orElse(Double.NaN);
    }

    @Override
    public double getMargin(Instant optimizedInstant, AngleCnec angleCnec, Unit unit) {
        if (angleMonitoringResult == null) {
            return raoResult.getMargin(optimizedInstant, angleCnec, unit);
        }
        unit.checkPhysicalParameter(PhysicalParameter.ANGLE);
        return getAngleCnecResult(optimizedInstant, angleCnec).map(CnecResult::getMargin).orElse(Double.NaN);
    }

    @Override
    public double getMinVoltage(Instant optimizedInstant, VoltageCnec voltageCnec, Unit unit) {
        if (voltageMonitoringResult == null) {
            return raoResult.getMinVoltage(optimizedInstant, voltageCnec, unit);
        }
        unit.checkPhysicalParameter(PhysicalParameter.VOLTAGE);
        return getVoltageCnecResult(optimizedInstant, voltageCnec)
                .map(r -> ((VoltageCnecValue) r.getValue()).minValue())
                .orElse(Double.NaN);
    }

    @Override
    public double getMaxVoltage(Instant optimizedInstant, VoltageCnec voltageCnec, Unit unit) {
        if (voltageMonitoringResult == null) {
            return raoResult.getMaxVoltage(optimizedInstant, voltageCnec, unit);
        }
        unit.checkPhysicalParameter(PhysicalParameter.VOLTAGE);
        return getVoltageCnecResult(optimizedInstant, voltageCnec)
                .map(r -> ((VoltageCnecValue) r.getValue()).maxValue())
                .orElse(Double.NaN);
    }

    @Override
    public double getMargin(Instant optimizedInstant, VoltageCnec voltageCnec, Unit unit) {
        if (voltageMonitoringResult == null) {
            return raoResult.getMargin(optimizedInstant, voltageCnec, unit);
        }
        unit.checkPhysicalParameter(PhysicalParameter.VOLTAGE);
        return getVoltageCnecResult(optimizedInstant, voltageCnec).map(CnecResult::getMargin).orElse(Double.NaN);
    }

    private Optional<CnecResult> getAngleCnecResult(Instant optimizedInstant, AngleCnec angleCnec) {
        checkOptimizationInstant(optimizedInstant, angleCnec);
        return angleMonitoringResult.getCnecResults().stream().filter(r -> r.getId().equals(angleCnec.getId())).findFirst();
    }

    private Optional<CnecResult> getVoltageCnecResult(Instant optimizedInstant, VoltageCnec voltageCnec) {
        checkOptimizationInstant(optimizedInstant, voltageCnec);
        return voltageMonitoringResult.getCnecResults().stream().filter(r -> r.getId().equals(voltageCnec.getId())).findFirst();
    }

    private void checkOptimizationInstant(Instant optimizedInstant, Cnec<?> cnec) {
        if (cnec.getState().getInstant() != optimizedInstant) {
            throw new OpenRaoException(
                    "Unexpected optimization instant for monitoring result: "
                            + (optimizedInstant == null ? "initial" : optimizedInstant.getId())
                            + ". Only optimization instant equal to cnec's instant is accepted: "
                            + cnec.getState().getInstant().getId()
            );
        }
    }

    @Override
    public Set<NetworkAction> getActivatedNetworkActionsDuringState(State state) {
        Set<NetworkAction> concatenatedActions = new HashSet<>(raoResult.getActivatedNetworkActionsDuringState(state));
        concatenatedActions.addAll(networkActionsAppliedByMonitoring(angleMonitoringResult, state));
        concatenatedActions.addAll(networkActionsAppliedByMonitoring(voltageMonitoringResult, state));
        return concatenatedActions;
    }

    private Set<NetworkAction> networkActionsAppliedByMonitoring(MonitoringResult monitoringResult, State state) {
        if (monitoringResult == null) {
            return Set.of();
        }
        return monitoringResult.getAppliedRas(state).stream()
                .filter(NetworkAction.class::isInstance)
                .map(NetworkAction.class::cast)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isActivatedDuringState(State state, RemedialAction<?> remedialAction) {
        boolean activatedByAngleMonitoring = angleMonitoringResult != null && angleMonitoringResult.getAppliedRas(state).contains(remedialAction);
        boolean activatedByVoltageMonitoring = voltageMonitoringResult != null && voltageMonitoringResult.getAppliedRas(state).contains(remedialAction);
        return activatedByAngleMonitoring || activatedByVoltageMonitoring || raoResult.isActivatedDuringState(state, remedialAction);
    }

    @Override
    public boolean isActivatedDuringState(State state, NetworkAction networkAction) {
        return isActivatedDuringState(state, (RemedialAction<?>) networkAction);
    }

    @Override
    public boolean isSecure(Instant optimizedInstant, PhysicalParameter... u) {
        List<PhysicalParameter> remaining = new ArrayList<>(Stream.of(u).sorted().toList());
        Boolean angleSecure = (angleMonitoringResult != null && remaining.remove(PhysicalParameter.ANGLE))
                ? angleMonitoringResult.getStatus().equals(Cnec.SecurityStatus.SECURE) : null;
        Boolean voltageSecure = (voltageMonitoringResult != null && remaining.remove(PhysicalParameter.VOLTAGE))
                ? voltageMonitoringResult.getStatus().equals(Cnec.SecurityStatus.SECURE) : null;
        return raoResult.isSecure(optimizedInstant, remaining.toArray(new PhysicalParameter[0]))
                && (angleSecure == null || angleSecure)
                && (voltageSecure == null || voltageSecure);
    }

    @Override
    public boolean isSecure(PhysicalParameter... u) {
        List<PhysicalParameter> remaining = new ArrayList<>(Stream.of(u).sorted().toList());
        Boolean angleSecure = (angleMonitoringResult != null && remaining.remove(PhysicalParameter.ANGLE))
                ? angleMonitoringResult.getStatus().equals(Cnec.SecurityStatus.SECURE) : null;
        Boolean voltageSecure = (voltageMonitoringResult != null && remaining.remove(PhysicalParameter.VOLTAGE))
                ? voltageMonitoringResult.getStatus().equals(Cnec.SecurityStatus.SECURE) : null;
        return raoResult.isSecure(remaining.toArray(new PhysicalParameter[0]))
                && (angleSecure == null || angleSecure)
                && (voltageSecure == null || voltageSecure);
    }

    @Override
    public boolean isSecure() {
        boolean angleSecure = angleMonitoringResult == null || angleMonitoringResult.getStatus().equals(Cnec.SecurityStatus.SECURE);
        boolean voltageSecure = voltageMonitoringResult == null || voltageMonitoringResult.getStatus().equals(Cnec.SecurityStatus.SECURE);
        return raoResult.isSecure() && angleSecure && voltageSecure;
    }
}

