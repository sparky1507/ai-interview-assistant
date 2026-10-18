package com.harsh.interviewassistant.security;

import com.harsh.interviewassistant.user.Role;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit test: no Spring context, runs in milliseconds. */
class JwtServiceTest {

    private static final String SECRET = randomSecret(48);
    private static final String ISSUER = "ai-interview-assistant";

    private final JwtService jwtService = new JwtService(props(SECRET, ISSUER));
    private final UserPrincipal user = new UserPrincipal(UUID.randomUUID(), "jane@example.com", null, Role.USER);

    @Test
    void roundTripPreservesIdentityAndRole() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.parseToken(token)).hasValueSatisfying(parsed -> {
            assertThat(parsed.getId()).isEqualTo(user.getId());
            assertThat(parsed.getEmail()).isEqualTo("jane@example.com");
            assertThat(parsed.getRole()).isEqualTo(Role.USER);
            assertThat(parsed.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
            assertThat(parsed.getPassword()).isNull();
        });
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(user);
        // Flip a character in the payload (middle segment): the signature no longer matches
        String[] parts = token.split("\\.");
        char c = parts[1].charAt(5);
        parts[1] = parts[1].substring(0, 5) + (c == 'A' ? 'B' : 'A') + parts[1].substring(6);
        String tampered = String.join(".", parts);

        assertThat(jwtService.parseToken(tampered)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        Clock twoDaysAgo = Clock.fixed(Instant.now().minus(Duration.ofDays(2)), ZoneOffset.UTC);
        String token = new JwtService(props(SECRET, ISSUER), twoDaysAgo).generateToken(user); // 1h lifetime

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String token = new JwtService(props(randomSecret(48), ISSUER)).generateToken(user);

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    void tokenFromDifferentIssuerIsRejected() {
        String token = new JwtService(props(SECRET, "someone-else")).generateToken(user);

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    void garbageAndBlankTokensAreRejectedWithoutThrowing() {
        assertThat(jwtService.parseToken("not-a-jwt")).isEmpty();
        assertThat(jwtService.parseToken("")).isEmpty();
    }

    @Test
    void weakSecretFailsFast() {
        // 16 bytes = 128 bits, below the 256 bits HS256 requires
        assertThatThrownBy(() -> new JwtService(props(randomSecret(16), ISSUER)))
                .isInstanceOf(WeakKeyException.class);
    }

    private static JwtProperties props(String secret, String issuer) {
        return new JwtProperties(secret, Duration.ofHours(1), issuer);
    }

    private static String randomSecret(int bytes) {
        byte[] key = new byte[bytes];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
