package com.ivan.nexus.application.site;

/**
 * Optional HTTPS reachability probe for a hostname (TLS handshake + HTTP response).
 */
public interface DomainHttpsProbe {
    /**
     * @return true if HTTPS responded successfully, false if the probe failed
     */
    boolean probe(String hostname);
}
