package com.ivan.nexus.application.architecturefixture;

import java.sql.Connection;

public record JdbcDependentApplicationFixture(Connection connection) {
}
