package com.turnospro.infrastructure.security;

import com.turnospro.core.domain.TenantId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret:ClaveSuperSecretaDePruebaParaFirmarTokensJWT123456}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    /**
     * Generates a signed JWT with the 'custom:tenant_id' claim (Used for Tests and Auth Service).
     */
    public String generateToken(String userId, TenantId tenantId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(userId)
                .claim("custom:tenant_id", tenantId.id())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Validates the JWT and securely extracts all claims payload.
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the TenantId from the validated JWT token.
     */
    public TenantId extractTenantId(String token) {
        Claims claims = extractClaims(token);
        String tenantStr = claims.get("custom:tenant_id", String.class);
        if (tenantStr == null || tenantStr.isBlank()) {
            throw new IllegalArgumentException("JWT token does not contain the mandatory 'custom:tenant_id' claim");
        }
        return new TenantId(tenantStr);
    }
}
