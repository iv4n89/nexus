package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.domain.database.MongoStatement;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoInterruptedException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class MongoQueryExecutor {
    private static final Set<String> SYSTEM_DBS = Set.of("admin", "local", "config");

    public QueryResult execute(ResolvedTarget target, MongoStatement stmt) {
        long start = System.nanoTime();
        try (MongoClient client = MongoClients.create(uri(target))) {
            MongoCollection<Document> collection = client.getDatabase(stmt.database()).getCollection(stmt.collection());
            return switch (stmt.op()) {
                case "find" -> find(collection, stmt, start);
                case "aggregate" -> aggregate(collection, stmt, start);
                case "insert" -> {
                    collection.insertOne(Document.parse(stmt.documentJson() == null ? "{}" : stmt.documentJson()));
                    yield writeResult(1, start);
                }
                case "update" -> {
                    Document filter = Document.parse(stmt.filterJson());
                    Document update = Document.parse(stmt.updateJson() == null ? "{}" : stmt.updateJson());
                    long modified = stmt.multi()
                            ? collection.updateMany(filter, update).getModifiedCount()
                            : collection.updateOne(filter, update).getModifiedCount();
                    yield writeResult(modified, start);
                }
                case "delete" -> {
                    Document filter = Document.parse(stmt.filterJson());
                    long deleted = stmt.multi()
                            ? collection.deleteMany(filter).getDeletedCount()
                            : collection.deleteOne(filter).getDeletedCount();
                    yield writeResult(deleted, start);
                }
                default -> throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
            };
        } catch (MongoInterruptedException | MongoExecutionTimeoutException ex) {
            throw new DomainException(NexusErrorCode.QUERY_TIMEOUT, "Query timed out");
        } catch (MongoException ex) {
            throw new DomainException(
                    NexusErrorCode.QUERY_FAILED,
                    SecretSanitizer.strip(target.password(), ex.getMessage()));
        }
    }

    public QueryResult preview(ResolvedTarget target, String database, String collection) {
        MongoStatement stmt = new MongoStatement(
                "find",
                database,
                collection,
                "{}",
                null,
                null,
                null,
                null,
                100,
                false,
                com.ivan.nexus.domain.database.StatementClass.READ,
                false);
        return execute(target, stmt);
    }

    public QueryResult updateCell(ResolvedTarget target, String database, String collection, String id, String field, Object value) {
        long start = System.nanoTime();
        try (MongoClient client = MongoClients.create(uri(target))) {
            MongoCollection<Document> coll = client.getDatabase(database).getCollection(collection);
            coll.updateOne(Filters.eq("_id", parseId(id)), Updates.set(field, value));
            return writeResult(1, start);
        } catch (MongoException ex) {
            throw new DomainException(
                    NexusErrorCode.QUERY_FAILED,
                    SecretSanitizer.strip(target.password(), ex.getMessage()));
        }
    }

    public MongoCatalog metadata(ResolvedTarget target) {
        try (MongoClient client = MongoClients.create(uri(target))) {
            List<MongoDb> databases = new ArrayList<>();
            for (String name : client.listDatabaseNames()) {
                if (SYSTEM_DBS.contains(name)) {
                    continue;
                }
                List<String> collections = new ArrayList<>();
                client.getDatabase(name).listCollectionNames().into(collections);
                databases.add(new MongoDb(name, List.copyOf(collections)));
            }
            return new MongoCatalog(databases);
        } catch (MongoException ex) {
            throw new DomainException(
                    NexusErrorCode.QUERY_FAILED,
                    SecretSanitizer.strip(target.password(), ex.getMessage()));
        }
    }

    private static QueryResult find(MongoCollection<Document> collection, MongoStatement stmt, long start) {
        Document filter = Document.parse(stmt.filterJson() == null ? "{}" : stmt.filterJson());
        FindIterable<Document> iterable = collection.find(filter)
                .limit(stmt.limit())
                .maxTime(10, TimeUnit.SECONDS);
        if (stmt.projectionJson() != null) {
            iterable = iterable.projection(Document.parse(stmt.projectionJson()));
        }
        List<Document> docs = new ArrayList<>();
        iterable.into(docs);
        return flatten(docs, start);
    }

    private static QueryResult aggregate(MongoCollection<Document> collection, MongoStatement stmt, long start) {
        List<Bson> pipeline = parsePipeline(stmt.pipelineJson());
        List<Document> docs = new ArrayList<>();
        collection.aggregate(pipeline).maxTime(10, TimeUnit.SECONDS).into(docs);
        return flatten(docs, start);
    }

    private static List<Bson> parsePipeline(String pipelineJson) {
        if (pipelineJson == null || pipelineJson.isBlank()) {
            return List.of();
        }
        Document wrapped = Document.parse("{\"p\":" + pipelineJson + "}");
        List<Document> stages = wrapped.getList("p", Document.class);
        return stages == null ? List.of() : List.copyOf(stages);
    }

    private static QueryResult flatten(List<Document> docs, long start) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        for (Document doc : docs) {
            columns.addAll(doc.keySet());
        }
        List<String> columnList = new ArrayList<>(columns);
        List<List<Object>> rows = new ArrayList<>();
        for (Document doc : docs) {
            List<Object> row = new ArrayList<>(columnList.size());
            for (String column : columnList) {
                row.add(mongoCell(doc.get(column)));
            }
            rows.add(row);
        }
        return new QueryResult(columnList, rows, false, elapsedMs(start), rows.size());
    }

    private static Object mongoCell(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        if (value instanceof Document document) {
            return document.toJson();
        }
        if (value instanceof List<?> list) {
            return list.toString();
        }
        return String.valueOf(value);
    }

    private static QueryResult writeResult(long count, long start) {
        return new QueryResult(
                List.of("updateCount"),
                List.of(List.of(count)),
                false,
                elapsedMs(start),
                1);
    }

    static Object parseId(String id) {
        if (id != null && id.length() == 24 && id.chars().allMatch(ch -> Character.digit(ch, 16) >= 0)) {
            return new ObjectId(id);
        }
        return id;
    }

    private static String uri(ResolvedTarget target) {
        if (target.username() == null || target.username().isBlank()) {
            return "mongodb://%s:%d".formatted(target.host(), target.port());
        }
        return "mongodb://%s:%s@%s:%d/admin".formatted(
                URLEncoder.encode(target.username(), StandardCharsets.UTF_8),
                URLEncoder.encode(target.password() == null ? "" : target.password(), StandardCharsets.UTF_8),
                target.host(),
                target.port());
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    public record MongoCatalog(List<MongoDb> databases) {}

    public record MongoDb(String name, List<String> collections) {}
}
