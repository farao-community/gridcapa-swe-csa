package com.farao_community.farao.swe_csa.app.dichotomy;

public interface DichotomyVariable<U extends DichotomyVariable<U>> {
    boolean isGreaterThan(U other);

    double distanceTo(U other);

    U halfRangeWith(U other);

    String print();
}
