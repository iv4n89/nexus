package com.ivan.nexus.domain.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorNormalizerTest {

    @Test
    void sameFingerprintForDifferentIps() {
        var a = ErrorNormalizer.normalize("Connection to 10.0.0.31 failed at 12:42");
        var b = ErrorNormalizer.normalize("Connection to 10.0.0.44 failed at 12:43");
        assertEquals(a.orElseThrow().fingerprint(), b.orElseThrow().fingerprint());
    }

    @Test
    void nonMatchingLineIsEmpty() {
        assertThat(ErrorNormalizer.normalize("INFO started successfully")).isEmpty();
        assertThat(ErrorNormalizer.normalize("request completed")).isEmpty();
    }

    @Test
    void uuidAndIso8601TimestampsCollapse() {
        var a = ErrorNormalizer.normalize(
                "ERROR user 550e8400-e29b-41d4-a716-446655440000 at 2026-01-15T12:42:00Z");
        var b = ErrorNormalizer.normalize(
                "ERROR user 123e4567-e89b-12d3-a456-426614174000 at 2026-09-16T08:01:02.123Z");
        assertThat(a).isPresent();
        assertThat(b).isPresent();
        assertThat(a.orElseThrow().fingerprint()).isEqualTo(b.orElseThrow().fingerprint());
    }

    @Test
    void digitRunsForPortsAndIdsCollapse() {
        var a = ErrorNormalizer.normalize("timeout connecting to port 8080 id 99887766");
        var b = ErrorNormalizer.normalize("timeout connecting to port 9090 id 11223344");
        assertThat(a.orElseThrow().fingerprint()).isEqualTo(b.orElseThrow().fingerprint());
    }

    @Test
    void fingerprintIsSha256HexOfNormalizedUtf8() {
        var result = ErrorNormalizer.normalize("ERROR boom").orElseThrow();
        assertThat(result.fingerprint()).matches("[0-9a-f]{64}");
        assertThat(result.normalized()).isNotBlank();
        assertThat(result.sampleMessage()).isEqualTo("ERROR boom");
    }

    @Test
    void stackFramesAreNotErrors() {
        assertThat(ErrorNormalizer.normalize(
                "        at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:125)"))
                .isEmpty();
        assertThat(ErrorNormalizer.normalize(
                "\tat org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:83)"))
                .isEmpty();
    }

    @Test
    void mongoDriverInfoMonitorIsNotAnError() {
        assertThat(ErrorNormalizer.normalize(
                "2026-09-16T13:42:27.045Z  INFO 1 --- [localhost:27017] org.mongodb.driver.cluster               : Exception in monitor thread while connecting to server localhost:27017"))
                .isEmpty();
    }

    @Test
    void caddyInfoFailedBufferIsNotAnError() {
        assertThat(ErrorNormalizer.normalize(
                "{\"level\":\"info\",\"ts\":1789556477.742,\"msg\":\"failed to sufficiently increase receive buffer size\"}"))
                .isEmpty();
    }

    @Test
    void leftoverNginxBindAndMongoSocketAreNotAppErrors() {
        assertThat(ErrorNormalizer.normalize(
                "nginx: [emerg] bind() to 0.0.0.0:80 failed (98: Address in use)"))
                .isEmpty();
        assertThat(ErrorNormalizer.normalize(
                "com.mongodb.MongoSocketOpenException: Exception opening socket"))
                .isEmpty();
        assertThat(ErrorNormalizer.normalize(
                "org.springframework.web.context.request.async.AsyncRequestNotUsableException: Response not usable after response errors."))
                .isEmpty();
        assertThat(ErrorNormalizer.normalize(
                "Caused by: java.net.ConnectException: Connection refused"))
                .isEmpty();
        assertThat(ErrorNormalizer.normalize(
                "java.lang.IllegalStateException: ResponseBodyEmitter has already completed"))
                .isEmpty();
    }
}
