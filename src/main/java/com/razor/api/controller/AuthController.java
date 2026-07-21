package com.razor.api.controller;

import com.razor.api.service.AuthService;
import com.razor.api.service.AuthService.LoginResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

/** Login do painel web: troca email + senha por um JWT (Bearer) usado nas rotas /api/admin. */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record LoginResponse(String token, UUID barbershopId, String role, String name, String email) {
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResult r = authService.login(request.email(), request.password());
            return ResponseEntity.ok(new LoginResponse(r.token(), r.barbershopId(), r.role().name(), r.name(), r.email()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciais invalidas."));
        }
    }
}
