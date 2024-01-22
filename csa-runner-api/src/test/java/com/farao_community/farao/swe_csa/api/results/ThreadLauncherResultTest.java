package com.farao_community.farao.swe_csa.api.results;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThreadLauncherResultTest {

    @Test
    void successResult() {
        String expectedResult = "SuccessResult";
        ThreadLauncherResult<String> result = ThreadLauncherResult.success(expectedResult);

        assertTrue(result.getResult().isPresent());
        assertFalse(result.hasError());
        assertNull(result.getException());
        assertEquals(expectedResult, result.getResult().get());
    }

    @Test
    void interruptResult() {
        ThreadLauncherResult<String> result = ThreadLauncherResult.interrupt();

        assertFalse(result.getResult().isPresent());
        assertFalse(result.hasError());
        assertNull(result.getException());
    }

    @Test
    void errorResult() {
        Exception expectedException = new RuntimeException("Test Exception");
        ThreadLauncherResult<String> result = ThreadLauncherResult.error(expectedException);

        assertFalse(result.getResult().isPresent());
        assertTrue(result.hasError());
        assertNotNull(result.getException());
        assertEquals(expectedException, result.getException());
    }

    @Test
    void getResultOnSuccess() {
        String expectedResult = "SuccessResult";
        ThreadLauncherResult<String> result = ThreadLauncherResult.success(expectedResult);

        assertEquals(expectedResult, result.getResult().get());
    }

    @Test
    void getResultOnInterrupt() {
        ThreadLauncherResult<String> result = ThreadLauncherResult.interrupt();

        assertFalse(result.getResult().isPresent());
    }

    @Test
    void getResultOnError() {
        Exception expectedException = new RuntimeException("Test Exception");
        ThreadLauncherResult<String> result = ThreadLauncherResult.error(expectedException);

        assertFalse(result.getResult().isPresent());
    }

    @Test
    void hasErrorOnSuccess() {
        String expectedResult = "SuccessResult";
        ThreadLauncherResult<String> result = ThreadLauncherResult.success(expectedResult);

        assertFalse(result.hasError());
    }

    @Test
    void hasErrorOnInterrupt() {
        ThreadLauncherResult<String> result = ThreadLauncherResult.interrupt();

        assertFalse(result.hasError());
    }

    @Test
    void hasErrorOnError() {
        Exception expectedException = new RuntimeException("Test Exception");
        ThreadLauncherResult<String> result = ThreadLauncherResult.error(expectedException);

        assertTrue(result.hasError());
    }

    @Test
    void getExceptionOnSuccess() {
        String expectedResult = "SuccessResult";
        ThreadLauncherResult<String> result = ThreadLauncherResult.success(expectedResult);

        assertNull(result.getException());
    }

    @Test
    void getExceptionOnInterrupt() {
        ThreadLauncherResult<String> result = ThreadLauncherResult.interrupt();

        assertNull(result.getException());
    }

    @Test
    void getExceptionOnError() {
        Exception expectedException = new RuntimeException("Test Exception");
        ThreadLauncherResult<String> result = ThreadLauncherResult.error(expectedException);

        assertNotNull(result.getException());
        assertEquals(expectedException, result.getException());
    }
}
