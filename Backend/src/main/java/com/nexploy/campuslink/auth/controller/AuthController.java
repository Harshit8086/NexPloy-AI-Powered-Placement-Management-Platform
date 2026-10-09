package com.nexploy.campuslink.auth.controller;

import com.nexploy.campuslink.auth.dto.ChangePasswordRequest;
import com.nexploy.campuslink.auth.dto.CurrentUserResponse;
import com.nexploy.campuslink.auth.dto.LoginRequest;
import com.nexploy.campuslink.auth.dto.LoginResponse;
import com.nexploy.campuslink.auth.dto.RefreshRequest;
import com.nexploy.campuslink.auth.dto.RecruiterRegisterRequest;
import com.nexploy.campuslink.auth.dto.StudentRegisterRequest;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.auth.service.JwtService;
import com.nexploy.campuslink.auth.service.RefreshTokenService;
import com.nexploy.campuslink.auth.service.RegistrationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsService userDetailsService;
    private final RegistrationService registrationService;
    private final CampusAccessService campusAccessService;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register/student")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse registerStudent(@Valid @RequestBody StudentRegisterRequest request) {
        return signIn(registrationService.registerStudent(request));
    }

    @PostMapping("/register/recruiter")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse registerRecruiter(@Valid @RequestBody RecruiterRegisterRequest request) {
        return signIn(registrationService.registerRecruiter(request));
    }

    /**
     * Signs a freshly registered user in, issuing the same access + refresh token pair a login
     * would, so the client lands authenticated without a second round-trip. Only STUDENT and
     * RECRUITER have registration endpoints; staff roles are invite-only.
     */
    private LoginResponse signIn(UserAccount account) {
        String accessToken = jwtService.generateToken(account);
        String refreshToken = refreshTokenService.issue(account.getUsername());
        return new LoginResponse(accessToken, refreshToken, "Bearer");
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
            UserDetails principal = (UserDetails) authentication.getPrincipal();
            String accessToken = jwtService.generateToken(principal);
            String refreshToken = refreshTokenService.issue(principal.getUsername());
            return new LoginResponse(accessToken, refreshToken, "Bearer");
        } catch (org.springframework.security.core.AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    /**
     * The authenticated caller's own identity. Backs the client's session bootstrap and the
     * shared header, which previously leaned on demo profiles. Returns only fields that exist on
     * the user account (username, role, campus) — no display name is invented.
     */
    @GetMapping("/me")
    public CurrentUserResponse me() {
        UserAccount user = campusAccessService.currentUser();
        return CurrentUserResponse.from(user);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());
        UserDetails principal = userDetailsService.loadUserByUsername(rotation.username());
        String accessToken = jwtService.generateToken(principal);
        return new LoginResponse(accessToken, rotation.rawToken(), "Bearer");
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    /**
     * Changes the signed-in user's password. The current password is re-verified against the
     * stored BCrypt hash before the new one is accepted; a mismatch is a 400. On success every
     * active refresh token is revoked so other devices must re-authenticate.
     */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UserAccount user = campusAccessService.currentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userAccountRepository.save(user);
        refreshTokenService.revokeAll(user.getUsername());
    }

    /**
     * Signs the user out of every device by revoking all their active refresh tokens. Access
     * tokens are stateless JWTs and stay valid until they expire; this stops session renewal
     * everywhere, which is the standard server-side lever.
     */
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll() {
        UserAccount user = campusAccessService.currentUser();
        refreshTokenService.revokeAll(user.getUsername());
    }
}
