package com.farao_community.farao.swe_csa.app.multi_border_monitoring;

import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.monitoring.MonitoringInput;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MultiBorderMonitoringInput {

    public record BorderMonitoringInput(Border border, Crac crac, RaoResult raoResult) { }

    private final Network network;
    private final Set<BorderMonitoringInput> inputs;
    private final Map<Border, BorderMonitoringInput> inputPerBorder;
    private final PhysicalParameter physicalParameter;
    private final ZonalData<Scalable> scalableZonalData;
    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;

    private final Map<PhysicalParameter, Unit> parameterToUnitMap = new EnumMap<>(Map.of(
                    PhysicalParameter.ANGLE, Unit.DEGREE,
                    PhysicalParameter.VOLTAGE, Unit.KILOVOLT,
                    PhysicalParameter.FLOW, Unit.AMPERE));

    public MultiBorderMonitoringInput(Network network, Set<BorderMonitoringInput> inputs, PhysicalParameter physicalParameter,
                                      ZonalData<Scalable> scalableZonalData, String loadFlowProvider, LoadFlowParameters loadFlowParameters) {
        this.network = network;
        this.physicalParameter = physicalParameter;
        this.scalableZonalData = scalableZonalData;
        this.loadFlowProvider = loadFlowProvider;
        this.loadFlowParameters = loadFlowParameters;
        this.inputPerBorder = inputs.stream()
                .collect(Collectors.toMap(
                        BorderMonitoringInput::border, Function.identity(), (a, b) -> {
                            throw new IllegalArgumentException("Duplicate input defined for border " + a.border());
                        }));
        this.inputs = Set.copyOf(this.inputPerBorder.values());
    }

    private BorderMonitoringInput getInput(Border border) {
        BorderMonitoringInput input = inputPerBorder.get(border);
        if (input == null) {
            throw new IllegalArgumentException("No input defined for border " + border);
        }
        return input;
    }

    public Crac getCracForBorder(Border border) {
        return getInput(border).crac();
    }

    public RaoResult getRaoResultForBorder(Border border) {
        return getInput(border).raoResult();
    }

    public Network getNetwork() {
        return network;
    }

    public PhysicalParameter getPhysicalParameter() {
        return physicalParameter;
    }

    public ZonalData<Scalable> getZonalScalableData() {
        return scalableZonalData;
    }

    public String getLoadFlowProvider() {
        return loadFlowProvider;
    }

    public LoadFlowParameters getLoadFlowParameters() {
        return loadFlowParameters;
    }

    public Set<Border> getBorders() {
        return inputs.stream().map(BorderMonitoringInput::border).collect(Collectors.toSet());
    }

    public Set<BorderMonitoringInput> getInputs() {
        return inputs;
    }

    public MonitoringInput getMonitoringInputForBorder(Border border) {
        BorderMonitoringInput input = getInput(border);
        return switch (physicalParameter) {
            case VOLTAGE -> MonitoringInput.buildWithVoltage(network, input.crac(), input.raoResult()).build();
            case ANGLE -> MonitoringInput.buildWithAngle(network, input.crac(), input.raoResult(), scalableZonalData).build();
            default -> throw new IllegalArgumentException("Unsupported physical parameter: " + physicalParameter);
        };
    }

    public Map<Border, Set<Cnec>> getCnecsPerBorder() {
        return inputs.stream().collect(Collectors.toMap(BorderMonitoringInput::border,
                i -> i.crac().getCnecs(physicalParameter)));
    }

    public boolean hasAnyCnecs() {
        return inputs.stream().map(i -> i.crac().getCnecs(physicalParameter)).anyMatch(set -> !set.isEmpty());
    }

    public Map<Border, State> getPreventiveStates() {
        Map<Border, State> preventiveStates = new EnumMap<>(Border.class);
        inputs.forEach(i -> preventiveStates.put(i.border(), i.crac().getPreventiveState()));
        return preventiveStates;
    }

    public boolean hasAnyPreventiveState() {
        return getPreventiveStates().values().stream().anyMatch(Objects::nonNull);
    }

    public Map<Border, Set<Cnec>> getPreventiveCnecs() {
        return getPreventiveCnecs(getPreventiveStates());
    }

    public Map<Border, Set<Cnec>> getPreventiveCnecs(Map<Border, State> preventiveStates) {
        return preventiveStates.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> getCracForBorder(e.getKey()).getCnecs(physicalParameter, e.getValue())));
    }

    public Map<Border, Set<Cnec>> getCnecsForBorders(Collection<Border> borders, State state) {
        return borders.stream().collect(Collectors.toMap(Function.identity(),
                border -> new HashSet<>(getCracForBorder(border).getCnecs(physicalParameter, state))));
    }

    public State getAnyPreventiveState() {
        return getPreventiveStates().values().stream()
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
    }

    public Unit getUnit() {
        return parameterToUnitMap.get(physicalParameter);
    }
}


