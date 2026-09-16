package com.ivan.nexus.interfaces.database;

import com.ivan.nexus.application.database.DiscoverProjectDatabases;
import com.ivan.nexus.application.database.EditDatabaseCell;
import com.ivan.nexus.application.database.GetDatabaseMetadata;
import com.ivan.nexus.application.database.PreviewTable;
import com.ivan.nexus.application.database.RunDatabaseQuery;
import com.ivan.nexus.domain.database.QueryResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/database")
public class DatabaseController {
    private final DiscoverProjectDatabases discover;
    private final GetDatabaseMetadata getMetadata;
    private final PreviewTable previewTable;
    private final RunDatabaseQuery runQuery;
    private final EditDatabaseCell editCell;

    public DatabaseController(
            DiscoverProjectDatabases discover,
            GetDatabaseMetadata getMetadata,
            PreviewTable previewTable,
            RunDatabaseQuery runQuery,
            EditDatabaseCell editCell) {
        this.discover = discover;
        this.getMetadata = getMetadata;
        this.previewTable = previewTable;
        this.runQuery = runQuery;
        this.editCell = editCell;
    }

    @GetMapping("/instances")
    public List<DatabaseDtos.InstanceResponse> instances(@PathVariable String projectId) {
        return discover.execute(projectId).stream()
                .map(instance -> new DatabaseDtos.InstanceResponse(
                        instance.id(),
                        instance.service(),
                        instance.engine(),
                        instance.status(),
                        instance.defaultDatabase()))
                .toList();
    }

    @GetMapping("/instances/{databaseId}/metadata")
    public DatabaseDtos.MetadataResponse metadata(
            @PathVariable String projectId,
            @PathVariable String databaseId) {
        GetDatabaseMetadata.Metadata metadata = getMetadata.execute(projectId, databaseId);
        if (metadata.sql() != null) {
            return DatabaseDtos.fromSql(metadata.engine(), metadata.sql());
        }
        return DatabaseDtos.fromMongo(metadata.mongo());
    }

    @GetMapping("/instances/{databaseId}/preview")
    public DatabaseDtos.QueryResponse preview(
            @PathVariable String projectId,
            @PathVariable String databaseId,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String table,
            @RequestParam(required = false) String mongoDatabase,
            @RequestParam(required = false) String collection) {
        return toResponse(previewTable.execute(projectId, databaseId, schema, table, mongoDatabase, collection));
    }

    @PostMapping("/instances/{databaseId}/query")
    public DatabaseDtos.QueryResponse query(
            @PathVariable String projectId,
            @PathVariable String databaseId,
            @RequestBody DatabaseDtos.QueryRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        return toResponse(runQuery.execute(
                projectId,
                databaseId,
                body.statement(),
                body.confirmDestructive(),
                role(authentication),
                authentication.getName(),
                clientIp(request)));
    }

    @PostMapping("/instances/{databaseId}/cell")
    public DatabaseDtos.QueryResponse cell(
            @PathVariable String projectId,
            @PathVariable String databaseId,
            @RequestBody DatabaseDtos.CellRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        return toResponse(editCell.execute(
                projectId,
                databaseId,
                body.schema(),
                body.table(),
                body.primaryKey(),
                body.column(),
                body.value(),
                body.mongoDatabase(),
                body.collection(),
                body.id(),
                body.field(),
                authentication.getName(),
                clientIp(request)));
    }

    private static DatabaseDtos.QueryResponse toResponse(QueryResult result) {
        return new DatabaseDtos.QueryResponse(
                result.columns(),
                result.rows(),
                result.truncated(),
                result.durationMs(),
                result.rowCount());
    }

    private static String role(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("VIEWER");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
