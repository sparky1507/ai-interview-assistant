package com.harsh.interviewassistant.security;

import com.harsh.interviewassistant.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies HS256 access tokens.
 *
 * Tokens are self-contained: sub = user id, plus email and role claims. Verifying a request needs no
 * DB lookup, so any app instance can authenticate it (horizontal scaling, no session store).
 * Trade-off: a token stays valid until it expires even if the user is deleted or their role changes.
 * Mitigations if needed later: short expiry + refresh tokens, or a token deny-list.
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** Clock is injectable so tests can mint already-expired tokens. */
    JwtService(JwtProperties properties, Clock clock) {
        // hmacShaKeyFor throws WeakKeyException for keys < 256 bits -> app refuses to start with a weak secret
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.properties = properties;
        this.clock = clock;
    }

    public String generateToken(UserPrincipal user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiration())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @return the principal if the token is well-formed, correctly signed, unexpired and from our issuer;
     *         empty otherwise. Never throws: an invalid token simply means "not authenticated".
     */
    public Optional<UserPrincipal> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new UserPrincipal(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    null, // no password in a token-based principal
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            // IllegalArgumentException/NPE: blank token, malformed UUID, or missing/unknown role claim
            log.debug("Rejected JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long expirationSeconds() {
        return properties.expiration().toSeconds();
    }
}
