package com.farao_community.farao.swe_csa.api.exception;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RaoInterruptionExceptionTest {

    @Test
    void testExceptionMessage() {
        String message = "Test interruption";
        RaoInterruptionException exception = new RaoInterruptionException(message);
        Assertions.assertEquals(message, exception.getMessage());
    }
}
