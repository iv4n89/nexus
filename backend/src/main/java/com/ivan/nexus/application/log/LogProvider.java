package com.ivan.nexus.application.log;

import java.util.List;
import java.util.function.Consumer;

public interface LogProvider {
    List<String> fetch(String containerId, int tail, Integer since, Integer until, boolean timestamps);

    AutoCloseable follow(String containerId, int tail, Integer since, Consumer<String> onLine, Runnable onComplete);
}
