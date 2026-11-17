package com.digination.urlshortener.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class AuthController {

    record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    record RegisterRequest(@NotBlank @Size(max = 64) String username, @Size(min = 8, max = 72) String password) {}

    record TokenResponse(String token) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest req) {
        if (!auth.register(req.username(), req.password())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.username(), req.password())
                .map(TokenResponse::new)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials"));
    }
}
