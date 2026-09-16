package com.ivan.nexus.domain.project;

public record Project(String id, String name, String status, int runningCount, int totalCount) {
}
