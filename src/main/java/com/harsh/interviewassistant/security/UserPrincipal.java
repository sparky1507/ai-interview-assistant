package com.harsh.interviewassistant.security;

import com.harsh.interviewassistant.user.Role;
import com.harsh.interviewassistant.user.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated user as Spring Security sees it. Kept separate from the JPA entity so the
 * security context never holds a managed entity (lazy-loading, accidental persistence).
 * passwordHash is only set during login (DaoAuthenticationProvider); principals rebuilt from a JWT have none.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final Role role;

    public UserPrincipal(UUID id, String email, String passwordHash, Role role) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // "ROLE_" prefix is what hasRole("ADMIN") checks for
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String toString() {
        // Never print the hash, even in debug logs
        return "UserPrincipal[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
