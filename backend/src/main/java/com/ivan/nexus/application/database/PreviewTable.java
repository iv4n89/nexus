package com.ivan.nexus.application.database;

import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.infrastructure.database.JdbcQueryExecutor;
import com.ivan.nexus.infrastructure.database.MongoQueryExecutor;
import org.springframework.stereotype.Service;

@Service
public class PreviewTable {
    private final DiscoverProjectDatabases discover;
    private final JdbcQueryExecutor jdbc;
    private final MongoQueryExecutor mongo;

    public PreviewTable(
            DiscoverProjectDatabases discover,
            JdbcQueryExecutor jdbc,
            MongoQueryExecutor mongo) {
        this.discover = discover;
        this.jdbc = jdbc;
        this.mongo = mongo;
    }

    public QueryResult execute(
            String projectId,
            String databaseId,
            String schema,
            String table,
            String mongoDatabase,
            String collection) {
        InstanceResolution resolution = GetDatabaseMetadata.requireReady(discover.resolve(projectId, databaseId));
        if (resolution.instance().engine() == DatabaseEngine.MONGO) {
            return mongo.preview(resolution.target(), mongoDatabase, collection);
        }
        return jdbc.preview(resolution.instance().engine(), resolution.target(), schema, table);
    }
}
