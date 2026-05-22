package fr.alb.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import fr.alb.model.PasswordResetToken;
import fr.alb.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuthService {



    @Transactional
    public AuthResult authenticate(String username, String password, String clientIp) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return AuthResult.failure("Identifiants requis");
        }

        User user = User.findByUsername(username.trim());
        if (user == null) {
            BcryptUtil.matches("dummy", "$2a$10$dummy.hash.to.prevent.timing.attacks");
            return AuthResult.failure("Identifiants invalides");
        }

        if (!user.active) {
            return AuthResult.failure("Compte désactivé");
        }

        if (!BcryptUtil.matches(password, user.passwordHash)) {
            return AuthResult.failure("Identifiants invalides");
        }

        user.lastLoginAt = Instant.now();
        user.lastLoginIp = clientIp;
        user.update();

        // Local password login is intended for dev/test runs. We hand back an opaque
        // session token (UUID) — Quarkus OIDC won't validate it as a real JWT, so this
        // path is only usable when the server runs with security disabled
        // (start.sh --no-auth) or for environments that do not enforce OIDC.
        String token = java.util.UUID.randomUUID().toString();
        return AuthResult.success(token, user);
    }

    @Transactional
    public User createUser(String username, String email, String password, String fullName, Set<String> roles) {
        if (User.findByUsername(username) != null) {
            throw new IllegalArgumentException("Nom d'utilisateur déjà existant");
        }
        if (User.findByEmail(email) != null) {
            throw new IllegalArgumentException("Email déjà utilisé");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Mot de passe trop court (minimum 8 caractères)");
        }

        User user = new User(username, email, fullName, roles);
        user.passwordHash = BcryptUtil.bcryptHash(password);
        user.persist();
        return user;
    }

    /**
     * Request a password reset for a user by email
     * Returns a reset token that can be used to reset the password
     * Token expires after 1 hour
     */
    @Transactional
    public PasswordResetResult requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return PasswordResetResult.failure("Email requis");
        }

        User user = User.findByEmail(email.trim());

        // Don't reveal whether the email exists in the system (security best practice)
        // Always return success to prevent email enumeration attacks
        if (user == null) {
            // Return success but no token is actually created
            return PasswordResetResult.success(null, "Si cet email existe, un lien de réinitialisation a été envoyé");
        }

        if (!user.active) {
            // Don't reveal that account is inactive
            return PasswordResetResult.success(null, "Si cet email existe, un lien de réinitialisation a été envoyé");
        }

        // Generate cryptographically secure random token
        String token = generateSecureToken();

        // Token expires in 1 hour
        Instant expiresAt = Instant.now().plusSeconds(3600);

        // Invalidate any existing unused tokens for this user
        PasswordResetToken.list("{userId: ?1, used: false}", user.id).forEach(existingToken -> {
            ((PasswordResetToken) existingToken).used = true;
            ((PasswordResetToken) existingToken).update();
        });

        // Create new reset token
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.userId = user.id;
        resetToken.token = token;
        resetToken.email = email;
        resetToken.expiresAt = expiresAt;
        resetToken.used = false;
        resetToken.persist();

        return PasswordResetResult.success(token, "Si cet email existe, un lien de réinitialisation a été envoyé");
    }

    /**
     * Reset password using a valid reset token
     */
    @Transactional
    public ResetPasswordResult resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            return ResetPasswordResult.failure("Token requis");
        }
        if (newPassword == null || newPassword.length() < 8) {
            return ResetPasswordResult.failure("Mot de passe trop court (minimum 8 caractères)");
        }

        PasswordResetToken resetToken = PasswordResetToken.findByToken(token);

        if (resetToken == null || !resetToken.isValid()) {
            return ResetPasswordResult.failure("Token invalide ou expiré");
        }

        User user = User.findById(resetToken.userId);
        if (user == null) {
            return ResetPasswordResult.failure("Utilisateur introuvable");
        }

        // Update password
        user.passwordHash = BcryptUtil.bcryptHash(newPassword);
        user.update();

        // Mark token as used
        resetToken.markAsUsed();
        resetToken.update();

        return ResetPasswordResult.success("Mot de passe réinitialisé avec succès");
    }

    /**
     * Validate a reset token without using it
     */
    public TokenValidationResult validateResetToken(String token) {
        if (token == null || token.isBlank()) {
            return TokenValidationResult.invalid("Token requis");
        }

        PasswordResetToken resetToken = PasswordResetToken.findByToken(token);

        if (resetToken == null || !resetToken.isValid()) {
            return TokenValidationResult.invalid("Token invalide ou expiré");
        }

        // Récupérer l'utilisateur associé au token
        User user = User.findById(resetToken.userId);
        if (user == null) {
            return TokenValidationResult.invalid("Utilisateur introuvable");
        }

        return TokenValidationResult.valid(resetToken.email, user);
    }

    /**
     * Generate a cryptographically secure random token
     */
    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32]; // 256 bits
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static class AuthResult {
        private final boolean success;
        private final String token;
        private final User user;
        private final String errorMessage;

        private AuthResult(boolean success, String token, User user, String errorMessage) {
            this.success = success;
            this.token = token;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public static AuthResult success(String token, User user) {
            return new AuthResult(true, token, user, null);
        }

        public static AuthResult failure(String errorMessage) {
            return new AuthResult(false, null, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getToken() { return token; }
        public User getUser() { return user; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class PasswordResetResult {
        private final boolean success;
        private final String token;
        private final String message;

        private PasswordResetResult(boolean success, String token, String message) {
            this.success = success;
            this.token = token;
            this.message = message;
        }

        public static PasswordResetResult success(String token, String message) {
            return new PasswordResetResult(true, token, message);
        }

        public static PasswordResetResult failure(String message) {
            return new PasswordResetResult(false, null, message);
        }

        public boolean isSuccess() { return success; }
        public String getToken() { return token; }
        public String getMessage() { return message; }
    }

    public static class ResetPasswordResult {
        private final boolean success;
        private final String message;

        private ResetPasswordResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ResetPasswordResult success(String message) {
            return new ResetPasswordResult(true, message);
        }

        public static ResetPasswordResult failure(String message) {
            return new ResetPasswordResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class TokenValidationResult {
        private final boolean valid;
        private final String email;
        private final User user;
        private final String message;

        private TokenValidationResult(boolean valid, String email, User user, String message) {
            this.valid = valid;
            this.email = email;
            this.user = user;
            this.message = message;
        }

        public static TokenValidationResult valid(String email, User user) {
            return new TokenValidationResult(true, email, user, "Token valide");
        }

        public static TokenValidationResult invalid(String message) {
            return new TokenValidationResult(false, null, null, message);
        }

        public boolean isValid() { return valid; }
        public String getEmail() { return email; }
        public User getUser() { return user; }
        public String getMessage() { return message; }
    }
}