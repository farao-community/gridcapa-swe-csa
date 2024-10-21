package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class GenericThreadLauncherTest {

    private class LaunchWithoutThreadableAnnotation {

        public Integer run(int steps) {
            Integer result = 1;
            for (int i = 1; i < steps; i++) {
                result *= i * i;
            }
            return result;
        }

    }

    private class LaunchWithMultipleThreadableAnnotation {

        @Threadable
        public Integer run(int steps) {
            Integer result = 1;
            for (int i = 1; i < steps; i++) {
                result *= i * i;
            }
            return result;
        }

        @Threadable
        public Integer run2(int steps) {
            Integer result = 1;
            for (int i = 1; i < steps; i++) {
                result += i * i;
            }
            return result;
        }

    }

    private class LaunchWithThreadableAnnotation {

        @Threadable
        public Integer run(int steps) {
            Integer result = 1;
            for (int i = 1; i < steps; i++) {
                result *= i;
            }
            return result;
        }

    }

    @Test
    void testNotAnnotatedClass() {
        int exception = 0;
        try {
            new GenericThreadLauncher<>(new LaunchWithoutThreadableAnnotation(), "withThreadable", 10);
        } catch (Exception e) {
            exception++;
            assertEquals(e.getClass(), CsaInternalException.class);
            assertEquals("the class com.farao_community.farao.swe_csa.app.GenericThreadLauncherTest.LaunchWithoutThreadableAnnotation does not have his running method annotated with @Threadable", e.getMessage());
        }
        assertEquals(1, exception);

    }

    @Test
    void testMultipleAnnotatedClass() {
        int exception = 0;
        try {
            new GenericThreadLauncher<>(new LaunchWithMultipleThreadableAnnotation(), "withThreadable", 10);
        } catch (Exception e) {
            exception++;
            assertEquals(e.getClass(), CsaInternalException.class);
            assertEquals("the class com.farao_community.farao.swe_csa.app.GenericThreadLauncherTest.LaunchWithMultipleThreadableAnnotation must have only one method annotated with @Threadable", e.getMessage());
        }
        assertEquals(1, exception);
    }
}
