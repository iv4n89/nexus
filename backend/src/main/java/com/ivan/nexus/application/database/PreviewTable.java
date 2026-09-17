package com.ivan.nexus.application.database;

import com.ivan.nexus.domain.database.ControlPlaneDatabase;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.QueryResult;
import org.springframework.stereotype.Service;

@Service
public class PreviewTable {
    private final DiscoverProjectDatabases discover;
    private final SqlExecutor jdbc;
    private final MongoExecutor mongo;

    public PreviewTable(
            DiscoverProjectDatabases discover,
            SqlExecutor jdbc,
            MongoExecutor mongo) {
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
            String collection,
            String role) {
        ControlPlaneDatabase.requireAdminForDataAccess(projectId, role);
        InstanceResolution resolution = GetDatabaseMetadata.requireReady(discover.resolve(projectId, databaseId));
        if (resolution.instance().engine() == DatabaseEngine.MONGO) {
            return mongo.preview(resolution.target(), mongoDatabase, collection);
        }
        return jdbc.preview(resolution.instance().engine(), resolution.target(), schema, table);
    }
}
