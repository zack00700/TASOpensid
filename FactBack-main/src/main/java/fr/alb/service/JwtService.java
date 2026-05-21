package fr.alb.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import fr.alb.model.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class JwtService {

    private static final Logger LOG = Logger.getLogger(JwtService.class);

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "app.jwt.duration", defaultValue = "3600")
    long jwtDuration;

    @ConfigProperty(name = "app.jwt.generate-tokens", defaultValue = "true")
    boolean generateTokens;

    @ConfigProperty(name = "app.jwt.private-key")
    java.util.Optional<String> privateKeyEnv;

    private PrivateKey privateKey;

    @PostConstruct
    void init() {
        if (!generateTokens) {
            LOG.info("JWT token generation disabled - using Azure AD authentication only");
            return;
        }

        try {
            loadPrivateKey();
        } catch (Exception e) {
            LOG.error("Impossible de charger la clé privée JWT", e);
            throw new RuntimeException("Échec du chargement de la clé JWT", e);
        }
    }

    private void loadPrivateKey() throws Exception {
        // Try loading from environment variable first (for production)
        if (privateKeyEnv.isPresent()) {
            LOG.info("Loading JWT private key from environment variable");
            String privateKeyPEM = privateKeyEnv.get()
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyPEM);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(privateKeySpec);
            LOG.info("Clé privée JWT chargée avec succès depuis la variable d'environnement");
            return;
        }

        // Fallback to file in classpath (for local development)
        try (InputStream is = getClass().getResourceAsStream("/privateKey.pem")) {
            if (is == null) {
                throw new RuntimeException("Clé privée non trouvée. Veuillez définir la variable d'environnement JWT_PRIVATE_KEY ou placer privateKey.pem dans src/main/resources/");
            }
            LOG.info("Loading JWT private key from classpath file");
            String privateKeyPEM = new String(is.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyPEM);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(privateKeySpec);
            LOG.info("Clé privée JWT chargée avec succès depuis le fichier");
        }
    }

    public String generateToken(User user) {
        if (!generateTokens) {
            throw new UnsupportedOperationException(
                "Local JWT token generation is disabled. Please use Azure AD authentication.");
        }

        if (user == null || !user.active) {
            throw new IllegalArgumentException("User invalide ou inactif");
        }

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtDuration);

        return Jwt.claims()
                .issuer(issuer)
                .subject(user.username)
                .audience("app-users")
                .issuedAt(now)
                .expiresAt(expiry)
                .claim("email", user.email)
                .claim("full_name", user.fullName)
                .claim("user_id", user.getId())
                .claim("groups", user.roles)
                .sign(privateKey);
    }
}