package com.ivan.nexus.application.database;

import com.ivan.nexus.domain.database.CellPatchGrouper;
import com.ivan.nexus.domain.database.MongoStatement;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;

import java.util.List;

public interface MongoExecutor {
    QueryResult execute(ResolvedTarget target, MongoStatement stmt);

    QueryResult preview(ResolvedTarget target, String database, String collection);

    QueryResult updateDocuments(
            ResolvedTarget target,
            String database,
            String collection,
            List<CellPatchGrouper.GroupedMongoUpdate> grouped);

    MongoCatalog metadata(ResolvedTarget target);

    record MongoCatalog(List<MongoDb> databases) {}

    record MongoDb(String name, List<String> collections) {}
}
