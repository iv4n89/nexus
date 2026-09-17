package com.ivan.nexus.application.database;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.database.ControlPlaneDatabase;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.database.MongoStatement;
import com.ivan.nexus.domain.database.MongoStatementClassifier;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.SqlStatementClassifier;
import com.ivan.nexus.domain.database.StatementClass;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.user.UserEntity;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class RunDatabaseQuery {
    private static final int QUERY_LIMIT = 500;

    private final DiscoverProjectDatabases discover;
    private final SqlExecutor jdbc;
    private final MongoExecutor mongo;
    private final RecordAudit recordAudit;
    private final UserJpaRepository users;
    private final MongoStatementParser mongoStatementParser;

    public RunDatabaseQuery(
            DiscoverProjectDatabases discover,
            SqlExecutor jdbc,
            MongoExecutor mongo,
            RecordAudit recordAudit,
            UserJpaRepository users,
            MongoStatementParser mongoStatementParser) {
        this.discover = discover;
        this.jdbc = jdbc;
        this.mongo = mongo;
        this.recordAudit = recordAudit;
        this.users = users;
        this.mongoStatementParser = mongoStatementParser;
    }

    public QueryResult execute(
            String projectId,
            String databaseId,
            String statement,
            boolean confirmDestructive,
            String role,
            String username,
            String ip) {
        ControlPlaneDatabase.requireAdminForDataAccess(projectId, role);
        InstanceResolution resolution = discover.resolve(projectId, databaseId);
        if (resolution.instance().status() == DatabaseStatus.UNREACHABLE || resolution.target() == null) {
            throw new DomainException(NexusErrorCode.DATABASE_UNREACHABLE, "Cannot connect");
        }
        StatementClass statementClass;
        boolean requiresConfirmation;
        MongoStatement mongoStatement = null;
        if (resolution.instance().engine() == com.ivan.nexus.domain.database.DatabaseEngine.MONGO) {
            mongoStatement = MongoStatementClassifier.classify(mongoStatementParser.parse(statement));
            statementClass = mongoStatement.statementClass();
            requiresConfirmation = mongoStatement.requiresConfirmation();
        } else {
            statementClass = SqlStatementClassifier.classify(statement);
            requiresConfirmation = statementClass == StatementClass.DESTRUCTIVE;
        }
        if ("VIEWER".equals(role) && statementClass != StatementClass.READ) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
        }
        if (requiresConfirmation && !confirmDestructive) {
            throw new DomainException(NexusErrorCode.CONFIRMATION_REQUIRED, "Confirmation required");
        }
        QueryResult result = resolution.instance().engine() == com.ivan.nexus.domain.database.DatabaseEngine.MONGO
                ? mongo.execute(resolution.target(), mongoStatement)
                : jdbc.query(resolution.instance().engine(), resolution.target(), statement, statementClass, QUERY_LIMIT);
        UUID userId = users.findByUsername(username).map(UserEntity::getId).orElse(null);
        String truncated = statement == null ? "" : statement.substring(0, Math.min(statement.length(), 2000));
        recordAudit.execute(
                userId,
                AuditAction.DB_QUERY,
                projectId,
                resolution.instance().service(),
                ip,
                Map.of(
                        "engine", resolution.instance().engine().name(),
                        "service", resolution.instance().service(),
                        "databaseId", databaseId,
                        "class", statementClass.name(),
                        "statement", truncated,
                        "rowCount", result.rowCount(),
                        "durationMs", result.durationMs()));
        return result;
    }
}
