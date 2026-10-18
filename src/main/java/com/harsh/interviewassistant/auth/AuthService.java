package com.harsh.interviewassistant.auth;

import com.harsh.interviewassistant.auth.dto.AuthResponse;
import com.harsh.interviewassistant.auth.dto.LoginRequest;
import com.harsh.interviewassistant.auth.dto.RegisterRequest;
import com.harsh.interviewassistant.common.exception.BadRequestException;
import com.harsh.interviewassistant.common.exception.ConflictException;
import com.harsh.interviewassistant.security.JwtService;
import com.harsh.interviewassistant.security.UserPrincipal;
import com.harsh.interviewassistant.user.User;
import com.harsh.interviewassistant.user.UserRepository;
import com.harsh.interviewassistant.user.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** BCrypt input limit. @Size counts characters; non-ASCII characters can take several bytes each. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (exceedsBcryptLimit(request.password())) {
            throw new BadRequestException("Password must be at most 72 bytes");
        }
        // Friendly fast path; the unique constraint (uk_users_email) is still the real guarantee under races
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .build();
        // saveAndFlush, not save: the INSERT runs now, so @CreationTimestamp is set for the response and a
        // concurrent duplicate fails here (DataIntegrityViolationException -> 409) instead of at commit time
        user = userRepository.saveAndFlush(user);

        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        if (exceedsBcryptLimit(request.password())) {
            throw new BadCredentialsException("Invalid email or password"); // can't match any stored hash
        }

        // Throws BadCredentialsException for both unknown email and wrong password (no user enumeration);
        // DaoAuthenticationProvider also runs a dummy hash for unknown emails, so timing doesn't leak it either
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return tokenFor(user);
    }

    private AuthResponse tokenFor(User user) {
        String token = jwtService.generateToken(UserPrincipal.from(user));
        return AuthResponse.bearer(token, jwtService.expirationSeconds(), UserResponse.from(user));
    }

    /** Emails are case-insensitive in practice; store and look up one canonical form. */
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }
}
