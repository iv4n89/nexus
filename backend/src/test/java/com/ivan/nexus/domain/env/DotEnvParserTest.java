package com.ivan.nexus.domain.env;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotEnvParserTest {

    @Test
    void parsesClassicLinesAndIgnoresComments() {
        Map<String, String> values = DotEnvParser.parse("""
                # comment
                NODE_ENV=production
                export API_KEY="s3cret"
                EMPTY=
                WITH_SPACE=hello world
                QUOTED='single'
                INLINE=value # trailing
                """);

        assertThat(values)
                .containsEntry("NODE_ENV", "production")
                .containsEntry("API_KEY", "s3cret")
                .containsEntry("EMPTY", "")
                .containsEntry("WITH_SPACE", "hello world")
                .containsEntry("QUOTED", "single")
                .containsEntry("INLINE", "value");
    }

    @Test
    void roundTripsOrderedMap() {
        LinkedHashMap<String, String> input = new LinkedHashMap<>();
        input.put("A", "1");
        input.put("B", "two words");
        input.put("C", "hash#tag");

        String formatted = DotEnvParser.format(input);
        assertThat(DotEnvParser.parse(formatted)).containsExactlyEntriesOf(input);
    }

    @Test
    void looksSecretHeuristic() {
        assertThat(DotEnvParser.looksSecret("DATABASE_URL")).isTrue();
        assertThat(DotEnvParser.looksSecret("API_KEY")).isTrue();
        assertThat(DotEnvParser.looksSecret("POSTGRES_PASSWORD")).isTrue();
        assertThat(DotEnvParser.looksSecret("NODE_ENV")).isFalse();
    }
}
