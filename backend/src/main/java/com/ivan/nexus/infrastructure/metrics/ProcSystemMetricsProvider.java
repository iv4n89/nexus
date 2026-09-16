package com.ivan.nexus.infrastructure.metrics;

import com.ivan.nexus.application.metrics.SystemMetricsProvider;
import com.ivan.nexus.domain.metrics.SystemMetrics;
import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ProcSystemMetricsProvider implements SystemMetricsProvider {
    private static final Logger log = LoggerFactory.getLogger(ProcSystemMetricsProvider.class);
    private static final Path UPTIME_PATH = Path.of("/proc/uptime");

    @Override
    public SystemMetrics get() {
        Memory memory = memory();
        Disk disk = disk();
        return new SystemMetrics(
                cpuPercent(),
                memory.usedBytes,
                memory.totalBytes,
                disk.usedBytes,
                disk.totalBytes,
                loadAverage(),
                uptimeSeconds());
    }

    private double cpuPercent() {
        try {
            OperatingSystemMXBean os = sunOs();
            if (os == null) {
                return 0;
            }
            double load = os.getCpuLoad();
            if (load < 0) {
                return 0;
            }
            return load * 100.0;
        } catch (RuntimeException ex) {
            log.warn("Unable to read host CPU load", ex);
            return 0;
        }
    }

    private Memory memory() {
        try {
            OperatingSystemMXBean os = sunOs();
            if (os == null) {
                return Memory.ZERO;
            }
            long total = totalMemory(os);
            long free = freeMemory(os);
            long used = Math.max(0, total - free);
            return new Memory(used, total);
        } catch (RuntimeException ex) {
            log.warn("Unable to read host memory", ex);
            return Memory.ZERO;
        }
    }

    private Disk disk() {
        try {
            var store = Files.getFileStore(Path.of("/"));
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            return new Disk(Math.max(0, total - usable), total);
        } catch (Exception ex) {
            log.warn("Unable to read root filesystem usage", ex);
            return Disk.ZERO;
        }
    }

    private double loadAverage() {
        try {
            double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            return load < 0 ? 0 : load;
        } catch (RuntimeException ex) {
            log.warn("Unable to read system load average", ex);
            return 0;
        }
    }

    private long uptimeSeconds() {
        try {
            if (Files.isRegularFile(UPTIME_PATH)) {
                String first = Files.readString(UPTIME_PATH).trim().split("\\s+")[0];
                return Math.round(Double.parseDouble(first));
            }
        } catch (Exception ex) {
            log.warn("Unable to read /proc/uptime; falling back to JVM uptime", ex);
        }
        try {
            return ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        } catch (RuntimeException ex) {
            log.warn("Unable to read JVM uptime", ex);
            return 0;
        }
    }

    private static OperatingSystemMXBean sunOs() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof OperatingSystemMXBean sunOs) {
            return sunOs;
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static long totalMemory(OperatingSystemMXBean os) {
        long total = os.getTotalMemorySize();
        if (total > 0) {
            return total;
        }
        return os.getTotalPhysicalMemorySize();
    }

    @SuppressWarnings("deprecation")
    private static long freeMemory(OperatingSystemMXBean os) {
        long free = os.getFreeMemorySize();
        if (free >= 0) {
            return free;
        }
        return os.getFreePhysicalMemorySize();
    }

    private record Memory(long usedBytes, long totalBytes) {
        static final Memory ZERO = new Memory(0, 0);
    }

    private record Disk(long usedBytes, long totalBytes) {
        static final Disk ZERO = new Disk(0, 0);
    }
}
