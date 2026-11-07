package com.kavindu.caresummaryapi.security;

import com.kavindu.caresummaryapi.audit.AuditEvent.Action;
import com.kavindu.caresummaryapi.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.Map;

/**
 *   POST /api/auth/login {"username","password"} -> {"token","roles","expiresAt"}
 *   GET  /api/auth/me                            -> who am I (needs a token)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank @Size(max = 100) String username,
                               @NotBlank @Size(max = 200) String password) {
    }

    private final AuthenticationManager authManager;
    private final TokenService tokens;
    private final AuditService audit;

    public AuthController(AuthenticationManager authManager, TokenService tokens, AuditService audit) {
        this.authManager = authManager;
        this.tokens = tokens;
        this.audit = audit;
    }

    @PostMapping("/login")
    public TokenService.Token login(@Valid @RequestBody LoginRequest req) {
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
            audit.record(auth.getName(), Action.LOGIN_SUCCESS, null, null);
            return tokens.create(auth);
        } catch (AuthenticationException e) {
            audit.record(req.username(), Action.LOGIN_FAILED, null, null);
            throw e;
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return Map.of("username", jwt.getSubject(), "roles", roles == null ? List.of() : roles);
    }

    /** Same message for wrong username and wrong password, so attackers can't learn which usernames exist. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail badLogin() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Wrong username or password");
    }
}
