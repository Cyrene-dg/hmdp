package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Local/integration adapter. A real deployment replaces it with the client's identity provider adapter. */
@Component
public class ConfiguredAdminAuthorizer implements AdminAuthorizer {

    private final byte[] expectedTokenDigest;
    private final AdminPrincipal principal;

    public ConfiguredAdminAuthorizer(
            @Value("${qinghe.admin.bearer-token:}") String bearerToken,
            @Value("${qinghe.admin.operator-id:local-admin}") String operatorId,
            @Value("${qinghe.admin.permissions:}") String permissions) {
        this.expectedTokenDigest = bearerToken == null || bearerToken.isEmpty()
                ? null : sha256(bearerToken);
        Set<String> granted = Arrays.stream(permissions.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        this.principal = new AdminPrincipal(operatorId, granted);
    }

    @Override
    public AdminPrincipal require(String authorizationHeader, String permission) {
        String token = bearerToken(authorizationHeader);
        if (expectedTokenDigest == null || token == null
                || !MessageDigest.isEqual(expectedTokenDigest, sha256(token))) {
            throw new QingheBusinessException(QingheErrorCode.UNAUTHENTICATED,
                    "admin authentication is missing or invalid");
        }
        if (!principal.hasPermission(permission)) {
            throw new QingheBusinessException(QingheErrorCode.FORBIDDEN,
                    "admin permission is required: " + permission);
        }
        return principal;
    }

    private static String bearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String value = header.substring("Bearer ".length()).trim();
        return value.isEmpty() ? null : value;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
