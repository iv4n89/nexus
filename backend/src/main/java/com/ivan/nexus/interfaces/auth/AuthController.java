package com.ivan.nexus.interfaces.auth;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import com.ivan.nexus.infrastructure.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserJpaRepository users;
    private final RecordAudit recordAudit;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(
            AuthenticationManager authenticationManager,
            UserJpaRepository users,
            RecordAudit recordAudit) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.recordAudit = recordAudit;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken());
    }

    @PostMapping("/login")
    public AuthUserResponse login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

        new ChangeSessionIdAuthenticationStrategy()
                .onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        UUID userId = users.findByUsername(authentication.getName())
                .orElseThrow()
                .getId();
        recordAudit.execute(
                userId,
                AuditAction.LOGIN,
                null,
                null,
                ClientIp.resolve(httpRequest),
                Map.of("username", authentication.getName()));

        return toResponse(authentication);
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        return toResponse(authentication);
    }

    private static AuthUserResponse toResponse(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElseThrow();
        return new AuthUserResponse(authentication.getName(), role);
    }

    public record LoginRequest(String username, String password) {}

    public record AuthUserResponse(String username, String role) {}

    public record CsrfResponse(String token) {}
}
