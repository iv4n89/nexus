package com.ivan.nexus.application.database;

import com.ivan.nexus.domain.database.DatabaseInstance;
import com.ivan.nexus.domain.database.ResolvedTarget;

public record InstanceResolution(DatabaseInstance instance, ResolvedTarget target) {}
