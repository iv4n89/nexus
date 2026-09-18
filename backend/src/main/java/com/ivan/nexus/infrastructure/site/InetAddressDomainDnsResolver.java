package com.ivan.nexus.infrastructure.site;

import com.ivan.nexus.application.site.DomainDnsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class InetAddressDomainDnsResolver implements DomainDnsResolver {
    private static final Logger log = LoggerFactory.getLogger(InetAddressDomainDnsResolver.class);

    @Override
    public boolean resolves(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(hostname.trim());
            return addresses != null && addresses.length > 0;
        } catch (UnknownHostException ex) {
            log.debug("DNS lookup failed for {}: {}", hostname, ex.getMessage());
            return false;
        }
    }
}
