package com.ivan.nexus.application.alert;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluateAlertsTransactionTest {

    @Test
    void executeIsNotTransactional() throws Exception {
        Method execute = EvaluateAlerts.class.getMethod("execute");
        assertThat(execute.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void persistRunsInItsOwnTransaction() throws Exception {
        Method persist = PersistAlertEvaluation.class.getMethod(
                "persist",
                com.ivan.nexus.domain.alert.AlertEvaluation.class,
                java.util.List.class,
                java.time.Instant.class);
        assertThat(persist.getAnnotation(Transactional.class)).isNotNull();
    }
}
