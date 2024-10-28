/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.swe_csa.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class InterruptionServiceTest {

    @Autowired
    InterruptionService interruptionService;

    private class MyThread extends Thread {

        public MyThread(String id) {
            super(id);
        }

        @Override
        public void run() {
            for (int i = 0; i < 10; i++) {
                await().atMost(i, SECONDS);
            }
        }
    }

    @Test
    void threadInterruption() {
        String jsonApiMessage =
            """
              {
              "data": {
                "type": "csa-interruption-request",
                "id": "myThread",
                "attributes": {}}
            }
            """;
        MyThread th = new MyThread("myThread");
        assertEquals(false,  isRunning("myThread").isPresent());

        th.start();
        assertEquals(true,  isRunning("myThread").isPresent());

        interruptionService.interruption(jsonApiMessage.getBytes(StandardCharsets.UTF_8));
        assertEquals(false,  isRunning("myThread").isPresent());

    }

    private Optional<Thread> isRunning(String id) {
        return Thread.getAllStackTraces()
                .keySet()
                .stream()
                .filter(t -> t.getName().equals(id))
                .findFirst();
    }

}
