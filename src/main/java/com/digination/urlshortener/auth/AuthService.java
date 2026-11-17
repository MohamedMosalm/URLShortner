package com.digination.urlshortener.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final JdbcClient db;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(JdbcClient db, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    public boolean register(String username, String password) {
        return db.sql("insert into users (username, password) values (?, ?) on conflict do nothing")
                .params(username, passwordEncoder.encode(password)).update() == 1;
    }

    public Optional<String> login(String username, String password) {
        boolean valid = db.sql("select password from users where username = ?").param(username)
                .query(String.class).optional()
                .map(hash -> passwordEncoder.matches(password, hash))
                .orElse(false);
        return valid ? Optional.of(issueToken(username)) : Optional.empty();
    }

    private String issueToken(String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
