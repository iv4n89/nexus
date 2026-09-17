package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.mongodb.MongoClientException;
import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MongoQueryExecutorTest {

    @Test
    void illegalOperationCodeMeansReplicaSetRequired() {
        DomainException ex = MongoQueryExecutor.mapMultiDocumentTxnFailure(
                new MongoException(20, "illegal operation"),
                "s3cret");
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        assertEquals("Multi-document Save needs a replica set", ex.getMessage());
    }

    @Test
    void replicaSetOrMongosMessageMeansReplicaSetRequired() {
        DomainException ex = MongoQueryExecutor.mapMultiDocumentTxnFailure(
                new MongoException("Transaction numbers are only allowed on a replica set member or mongos"),
                "s3cret");
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        assertEquals("Multi-document Save needs a replica set", ex.getMessage());
    }

    @Test
    void wrappedIllegalOperationCauseMeansReplicaSetRequired() {
        MongoException wrapped = new MongoClientException(
                "This MongoDB deployment does not support retryable writes. Please add retryWrites=false to your connection string.",
                new MongoException(20, "Transaction numbers are only allowed on a replica set member or mongos"));
        DomainException ex = MongoQueryExecutor.mapMultiDocumentTxnFailure(wrapped, "s3cret");
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        assertEquals("Multi-document Save needs a replica set", ex.getMessage());
    }

    @Test
    void otherMongoFailuresStayQueryFailedAndSanitized() {
        DomainException ex = MongoQueryExecutor.mapMultiDocumentTxnFailure(
                new MongoException(11000, "E11000 duplicate key s3cret"),
                "s3cret");
        assertEquals(NexusErrorCode.QUERY_FAILED, ex.getCode());
        assertFalse(ex.getMessage().contains("s3cret"));
    }
}
