package com.retrofit.backend.controller;

import com.retrofit.backend.auth.AuthRequest;
import com.retrofit.backend.auth.AuthResponse;
import com.retrofit.backend.dto.AdminDTO;
import com.retrofit.backend.dto.UserDTO;
import com.retrofit.backend.dto.UserProfileDTO;
import com.retrofit.backend.model.User;
import com.retrofit.backend.repository.UserRepository;
import com.retrofit.backend.service.AuthService;
import com.retrofit.backend.service.UserService;
import com.retrofit.backend.service.RefreshTokenService;
import com.retrofit.backend.auth.jwt.JwtUtil;
import com.retrofit.backend.auth.TokenRefreshRequest;
import com.retrofit.backend.model.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SECURITY_CREATE')")
    @PostMapping("/registerAdmin")
    public ResponseEntity<UserDTO> register(@RequestBody AdminDTO request) {
        return ResponseEntity.ok(userService.registerAdmin(request));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDTO> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails.getUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<String> permisos = user.getRole().getPermissions().stream()
                .map(permission -> permission.getName())
                .collect(Collectors.toList());

        UserProfileDTO dto = UserProfileDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .lastName(user.getLastName())
                .role(user.getRole().getName())
                .permissions(permisos)
                .requirePasswordChange(user.isRequirePasswordChange())
                .build();

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        final String jwt = authService.login(request);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(request.getUsername());
        return ResponseEntity.ok(new AuthResponse(jwt, refreshToken.getToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();
        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
                    String token = jwtUtil.generateToken(userDetails);
                    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getUsername());
                    return ResponseEntity.ok(new AuthResponse(token, newRefreshToken.getToken()));
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserDetails userDetails, @RequestBody Map<String, String> request) {
        String newPassword = request.get("newPassword");
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La nueva contraseña es requerida"));
        }
        
        userService.changePassword(userDetails.getUsername(), newPassword);
        
        return ResponseEntity.ok(Map.of("message", "Contraseña cambiada exitosamente"));
    }
}
