package com.farao_community.farao.swe_csa.app.dichotomy;

public interface IndexStrategy<U extends DichotomyVariable<U>> {
    double EPSILON = 1e-3;

    U nextValue(Index<?, U> index);

    default boolean precisionReached(Index<?, U> index) {
        if (index.lowestInvalidStep() != null && index.lowestInvalidStep().getLeft() .distanceTo(index.minValue()) < EPSILON) {
            return true;
        }
        if (index.highestValidStep() != null && index.highestValidStep().getLeft().distanceTo(index.maxValue()) < EPSILON) {
            return true;
        }
        if (index.lowestInvalidStep() == null || index.highestValidStep() == null) {
            return false;
        }
        return index.highestValidStep().getLeft().distanceTo(index.lowestInvalidStep().getLeft()) <= index.precision();
    }

}
