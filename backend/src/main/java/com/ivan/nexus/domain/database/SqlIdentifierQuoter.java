package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

public final class SqlIdentifierQuoter {
    private SqlIdentifierQuoter() {}

    public static String quote(DatabaseEngine engine, String identifier) {
        if (identifier == null || identifier.indexOf('\0') >= 0) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Invalid identifier");
        }
        if (engine == DatabaseEngine.MYSQL) {
            return "`" + identifier.replace("`", "``") + "`";
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
