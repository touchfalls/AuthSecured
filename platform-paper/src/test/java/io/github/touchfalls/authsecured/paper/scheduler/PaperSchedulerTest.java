package io.github.touchfalls.authsecured.paper.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PaperSchedulerTest {

    @Test
    void paperApiAloneIsNotDetectedAsFolia() {
        // The Folia scheduler is only chosen when the Folia server itself is running.
        assertFalse(PaperScheduler.isFolia());
    }
}
