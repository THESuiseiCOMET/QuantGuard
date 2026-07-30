package com.vanyiung.quantguard.desktop;

import com.vanyiung.quantguard.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@Profile("desktop")
@RequestMapping("/api/desktop")
public class DesktopShutdownController {

    static final String TOKEN_HEADER = "X-QuantGuard-Shutdown-Token";

    private final DesktopShutdownCoordinator shutdownCoordinator;
    private final String expectedToken;

    public DesktopShutdownController(
            DesktopShutdownCoordinator shutdownCoordinator,
            @Value("${QUANTGUARD_DESKTOP_SHUTDOWN_TOKEN:}") String expectedToken) {
        this.shutdownCoordinator = shutdownCoordinator;
        this.expectedToken = expectedToken;
    }

    @PostMapping("/shutdown")
    public ResponseEntity<ApiResponse<Void>> shutdown(
            @RequestHeader(name = TOKEN_HEADER, required = false) String suppliedToken,
            HttpServletRequest request) {
        if (expectedToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!isLoopback(request.getRemoteAddr()) || !tokenMatches(suppliedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        shutdownCoordinator.requestShutdown();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(null));
    }

    private boolean tokenMatches(String suppliedToken) {
        if (suppliedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean isLoopback(String remoteAddress) {
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
