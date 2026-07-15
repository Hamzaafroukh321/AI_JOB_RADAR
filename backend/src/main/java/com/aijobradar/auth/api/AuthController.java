package com.aijobradar.auth.api;

import com.aijobradar.auth.application.AuthenticatedUser;
import com.aijobradar.auth.application.CurrentUserService;
import com.aijobradar.auth.application.LoginAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthenticationManager manager;
  private final SecurityContextRepository contexts;
  private final CurrentUserService currentUsers;
  private final LoginAttemptLimiter limiter;

  public AuthController(
      AuthenticationManager manager,
      SecurityContextRepository contexts,
      CurrentUserService currentUsers,
      LoginAttemptLimiter limiter) {
    this.manager = manager;
    this.contexts = contexts;
    this.currentUsers = currentUsers;
    this.limiter = limiter;
  }

  @PostMapping("/login")
  public AuthenticatedUser login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    String key = request.getRemoteAddr() + ":" + body.email().toLowerCase();
    if (limiter.isBlocked(key))
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
    try {
      Authentication authentication =
          manager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  body.email().toLowerCase(), body.password()));
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      contexts.saveContext(context, request, response);
      limiter.succeeded(key);
      return currentUsers.recordLogin(authentication);
    } catch (BadCredentialsException exception) {
      limiter.failed(key);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
  }

  @GetMapping("/me")
  public AuthenticatedUser me(Authentication authentication) {
    return currentUsers.require(authentication);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    new SecurityContextLogoutHandler().logout(request, response, authentication);
  }

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
}
