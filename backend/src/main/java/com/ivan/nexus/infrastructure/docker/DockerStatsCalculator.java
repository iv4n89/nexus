package com.ivan.nexus.infrastructure.docker;

import com.github.dockerjava.api.model.CpuStatsConfig;
import com.github.dockerjava.api.model.CpuUsageConfig;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.github.dockerjava.api.model.StatisticNetworksConfig;
import com.github.dockerjava.api.model.Statistics;
import com.ivan.nexus.domain.metrics.ContainerMetrics;

import java.util.List;
import java.util.Map;

final class DockerStatsCalculator {
    private DockerStatsCalculator() {
    }

    static double cpuPercent(
            Long totalUsage,
            Long preTotalUsage,
            Long systemCpuUsage,
            Long preSystemCpuUsage,
            Long onlineCpus,
            Integer perCpuCount) {
        long cpuDelta = nz(totalUsage) - nz(preTotalUsage);
        long systemDelta = nz(systemCpuUsage) - nz(preSystemCpuUsage);
        long ncpu = ncpu(onlineCpus, perCpuCount);
        return cpuPercent(cpuDelta, systemDelta, ncpu);
    }

    static double cpuPercent(long cpuDelta, long systemDelta, long ncpu) {
        return systemDelta > 0 && cpuDelta >= 0
                ? (cpuDelta / (double) systemDelta) * ncpu * 100.0
                : 0.0;
    }

    static ContainerMetrics toMetrics(String containerId, Statistics statistics) {
        if (statistics == null) {
            return zeros(containerId);
        }
        return new ContainerMetrics(
                containerId,
                cpuPercent(statistics),
                memoryBytes(statistics.getMemoryStats(), true),
                memoryBytes(statistics.getMemoryStats(), false),
                networkBytes(statistics.getNetworks(), true),
                networkBytes(statistics.getNetworks(), false));
    }

    static ContainerMetrics zeros(String containerId) {
        return new ContainerMetrics(containerId, 0, 0, 0, 0, 0);
    }

    private static double cpuPercent(Statistics statistics) {
        CpuStatsConfig cpu = statistics.getCpuStats();
        CpuStatsConfig pre = statistics.getPreCpuStats();
        CpuUsageConfig usage = cpu == null ? null : cpu.getCpuUsage();
        CpuUsageConfig preUsage = pre == null ? null : pre.getCpuUsage();
        List<Long> perCpu = usage == null ? null : usage.getPercpuUsage();
        Integer perCpuCount = perCpu == null ? null : perCpu.size();
        return cpuPercent(
                usage == null ? null : usage.getTotalUsage(),
                preUsage == null ? null : preUsage.getTotalUsage(),
                cpu == null ? null : cpu.getSystemCpuUsage(),
                pre == null ? null : pre.getSystemCpuUsage(),
                cpu == null ? null : cpu.getOnlineCpus(),
                perCpuCount);
    }

    private static long memoryBytes(MemoryStatsConfig memory, boolean used) {
        if (memory == null) {
            return 0;
        }
        Long value = used ? memory.getUsage() : memory.getLimit();
        return nz(value);
    }

    private static long networkBytes(Map<String, StatisticNetworksConfig> networks, boolean rx) {
        if (networks == null || networks.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (StatisticNetworksConfig network : networks.values()) {
            if (network == null) {
                continue;
            }
            total += nz(rx ? network.getRxBytes() : network.getTxBytes());
        }
        return total;
    }

    private static long ncpu(Long onlineCpus, Integer perCpuCount) {
        if (onlineCpus != null && onlineCpus > 0) {
            return onlineCpus;
        }
        if (perCpuCount != null && perCpuCount > 0) {
            return perCpuCount;
        }
        return 1;
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
