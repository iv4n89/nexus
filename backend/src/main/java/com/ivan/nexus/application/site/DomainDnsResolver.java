package com.ivan.nexus.application.site;

/**
 * Outbound port for DNS resolution used by domain health checks.
 */
public interface DomainDnsResolver {
    boolean resolves(String hostname);
}
