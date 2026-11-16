package com.resolveai.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    @PostMapping("/register")
    public AuthService.AuthResponse register(@RequestBody @jakarta.validation.Valid RegisterRequest request) {
        return authService.register(request.name(), request.email(), request.password());
    }

    @PostMapping("/login")
    public AuthService.AuthResponse login(@RequestBody @jakarta.validation.Valid LoginRequest request) {
        return authService.login(request.email(), request.password());
    }
}