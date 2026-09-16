package com.ivan.nexus.application.log;

import java.util.List;

public interface LogProvider {
    List<String> fetch(String containerId, int tail, Integer since, Integer until, boolean timestamps);
}
