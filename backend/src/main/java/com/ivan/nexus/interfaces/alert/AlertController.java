package com.ivan.nexus.interfaces.alert;

import com.ivan.nexus.application.alert.AcknowledgeAlert;
import com.ivan.nexus.application.alert.GetAlerts;
import com.ivan.nexus.domain.alert.AlertStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AlertController {
    private final GetAlerts getAlerts;
    private final AcknowledgeAlert acknowledgeAlert;

    public AlertController(GetAlerts getAlerts, AcknowledgeAlert acknowledgeAlert) {
        this.getAlerts = getAlerts;
        this.acknowledgeAlert = acknowledgeAlert;
    }

    @GetMapping("/api/alerts")
    public List<AlertResponse> list(@RequestParam(required = false) List<AlertStatus> status) {
        return getAlerts.execute(status);
    }

    @PostMapping("/api/alerts/{id}/acknowledge")
    public AlertResponse acknowledge(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request) {
        return acknowledgeAlert.execute(id, authentication.getName(), clientIp(request));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
