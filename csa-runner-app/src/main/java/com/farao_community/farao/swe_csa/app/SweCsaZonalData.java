package com.farao_community.farao.swe_csa.app;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.glsk.commons.ZonalDataImpl;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.*;
import com.powsybl.openrao.commons.EICode;

import java.util.*;
import java.util.stream.Collectors;

public final class SweCsaZonalData {

    private SweCsaZonalData() {
        //private constructor
    }

    public static ZonalData<Scalable> getZonalData(Network network) {
        Map<Country, List<Generator>> generatorsByCountries = network.getGeneratorStream()
            .filter(SweCsaZonalData::isCorrect)
            .collect(Collectors.toMap(generator -> generator.getTerminal().getVoltageLevel().getSubstation().map(Substation::getNullableCountry).orElse(null),
                List::of,
                (list1, list2) -> {
                    List<Generator> list3 = new ArrayList<>(list1);
                    list3.addAll(list2);
                    return list3;
                }));
        ZonalData<Scalable> zonalData = new ZonalDataImpl<>(new HashMap<>());
        Set<Country> sweCountries = new HashSet<>(Arrays.asList(Country.FR, Country.PT, Country.ES));
        for (Map.Entry<Country, List<Generator>> entry : generatorsByCountries.entrySet().stream().filter(entry -> sweCountries.contains(entry.getKey())).collect(Collectors.toSet())) {
            List<Scalable> scalables = new ArrayList<>();
            List<Double> percentages = new ArrayList<>();
            Country c = entry.getKey();
            List<Generator> generators = entry.getValue();
            //calculate sum P of country's generators
            double totalCountryP = generators.stream().mapToDouble(SweCsaZonalData::pseudoTargetP).sum();
            //calculate factor of each generator
            generators.forEach(generator -> {
                double generatorPercentage =  100 * pseudoTargetP(generator) / totalCountryP;
                percentages.add(generatorPercentage);
                scalables.add(Scalable.onGenerator(generator.getId()));
            });
            Scalable scalable = Scalable.proportional(percentages, scalables);
            zonalData.addAll(new ZonalDataImpl<>(Collections.singletonMap(new EICode(Country.valueOf(c.toString())).getAreaCode(), scalable)));
        }

        return zonalData;
    }

    private static double pseudoTargetP(Generator generator) {
        return Math.max(1e-5, Math.abs(generator.getTargetP()));
    }

    private static boolean isCorrect(Injection<?> injection) {
        return injection != null &&
            injection.getTerminal().isConnected() &&
            injection.getTerminal().getBusView().getBus() != null &&
            injection.getTerminal().getBusView().getBus().isInMainSynchronousComponent();
    }
}
