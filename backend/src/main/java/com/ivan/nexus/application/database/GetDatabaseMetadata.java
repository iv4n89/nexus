package com.ivan.nexus.application.database;

import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

@Service
public class GetDatabaseMetadata {
    private final DiscoverProjectDatabases discover;
    private final SqlExecutor jdbc;
    private final MongoExecutor mongo;

    public GetDatabaseMetadata(
            DiscoverProjectDatabases discover,
            SqlExecutor jdbc,
            MongoExecutor mongo) {
        this.discover = discover;
        this.jdbc = jdbc;
        this.mongo = mongo;
    }

    public Metadata execute(String projectId, String databaseId) {
        InstanceResolution resolution = requireReady(discover.resolve(projectId, databaseId));
        if (resolution.instance().engine() == DatabaseEngine.MONGO) {
            return new Metadata(DatabaseEngine.MONGO, null, mongo.metadata(resolution.target()));
        }
        return new Metadata(resolution.instance().engine(), jdbc.metadata(resolution.instance().engine(), resolution.target()), null);
    }

    static InstanceResolution requireReady(InstanceResolution resolution) {
        if (resolution.instance().status() == DatabaseStatus.UNREACHABLE || resolution.target() == null) {
            throw new DomainException(NexusErrorCode.DATABASE_UNREACHABLE, "Cannot connect");
        }
        return resolution;
    }

    public record Metadata(
            DatabaseEngine engine,
            SqlExecutor.SqlCatalog sql,
            MongoExecutor.MongoCatalog mongo) {}
}
