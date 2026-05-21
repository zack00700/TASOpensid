package fr.alb.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import fr.alb.model.Asset;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Resource sécurisé pour la gestion des assets/fichiers.
 * Remplace AssetResource avec corrections de sécurité majeures.
 */
@Path("/assets")
@Produces(MediaType.APPLICATION_JSON)
public class AssetResource {

    private static final Logger LOG = Logger.getLogger(AssetResource.class);

    // Configuration sécurisée
    @ConfigProperty(name = "app.upload.base-path", defaultValue = "/tmp/secure-uploads")
    String uploadBasePath;

    @ConfigProperty(name = "app.upload.max-file-size", defaultValue = "5242880") // 5MB
    long maxFileSize;

    @ConfigProperty(name = "app.upload.max-files-per-request", defaultValue = "10")
    int maxFilesPerRequest;

    // Types MIME autorisés avec validation stricte
    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
            "image/png", Set.of("png"),
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/svg+xml", Set.of("svg"),
            "application/pdf", Set.of("pdf")
    );

    // Signatures de fichiers (magic numbers) pour validation du contenu réel
    private static final Map<String, byte[]> FILE_SIGNATURES = Map.of(
            "png", new byte[]{(byte)0x89, 0x50, 0x4E, 0x47},
            "jpg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF},
            "pdf", new byte[]{0x25, 0x50, 0x44, 0x46} // %PDF
    );

    // Pattern pour nettoyer les noms de fichiers
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("\\.\\.[\\\\/]");

    @PostConstruct
    void init() {
        try {
            // Créer le dossier d'upload sécurisé avec permissions restrictives
            java.nio.file.Path uploadDir = Paths.get(uploadBasePath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                // Permissions restrictives (propriétaire seulement)
                uploadDir.toFile().setReadable(false, false);
                uploadDir.toFile().setReadable(true, true);
                uploadDir.toFile().setWritable(false, false);
                uploadDir.toFile().setWritable(true, true);
                uploadDir.toFile().setExecutable(false, false);
                uploadDir.toFile().setExecutable(true, true);
            }
            LOG.infof("Upload directory initialized: %s", uploadDir.toAbsolutePath());
        } catch (IOException e) {
            LOG.error("Failed to initialize upload directory", e);
            throw new RuntimeException("Upload service unavailable", e);
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("ROLE_TEMPLATES_ADMIN")
    public Response upload(@RestForm("file") List<FileUpload> files) {

        // Validation préliminaire
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("No files provided");
        }

        if (files.size() > maxFilesPerRequest) {
            throw new BadRequestException("Too many files. Maximum allowed: " + maxFilesPerRequest);
        }

        List<Map<String, Object>> uploadedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (FileUpload file : files) {
            try {
                Map<String, Object> result = processFile(file);
                uploadedFiles.add(result);
            } catch (SecurityException | IllegalArgumentException e) {
                LOG.warnf("File upload rejected: %s - %s", file.fileName(), e.getMessage());
                errors.add(String.format("File '%s': %s", file.fileName(), e.getMessage()));
            } catch (Exception e) {
                LOG.errorf(e, "Unexpected error processing file: %s", file.fileName());
                errors.add(String.format("File '%s': Upload failed", file.fileName()));
            }
        }

        // Construire la réponse
        Map<String, Object> response = Map.of(
                "uploaded", uploadedFiles,
                "errors", errors,
                "summary", Map.of(
                        "total", files.size(),
                        "successful", uploadedFiles.size(),
                        "failed", errors.size()
                )
        );

        if (uploadedFiles.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }

        return Response.ok(response).build();
    }

    private Map<String, Object> processFile(FileUpload file) throws IOException {

        // 1. Validation de base
        validateFileBasics(file);

        // 2. Validation du nom de fichier et extension
        String cleanFileName = sanitizeFileName(file.fileName());
        String extension = getFileExtension(cleanFileName).toLowerCase();

        // 3. Validation du type MIME et extension
        validateFileType(file.contentType(), extension);

        // 4. Validation du contenu réel (magic numbers)
        validateFileContent(file.uploadedFile(), extension);

        // 5. Génération d'un nom sécurisé
        String secureId = UUID.randomUUID().toString();
        String secureFileName = secureId + "_" + cleanFileName;

        // 6. Stockage sécurisé
        java.nio.file.Path targetPath = storeFileSecurely(file, secureFileName);

        // 7. Création de l'entité Asset
        Asset asset = createAssetEntity(secureId, cleanFileName, file, targetPath);
        asset.persist();

        LOG.infof("File uploaded successfully: %s -> %s", file.fileName(), secureFileName);

        return Map.of(
                "id", asset.getId(),
                "name", asset.name,
                "url", asset.url,
                "size", asset.size,
                "contentType", asset.contentType
        );
    }

    private void validateFileBasics(FileUpload file) {
        if (file.fileName() == null || file.fileName().isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }

        if (file.size() <= 0) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.size() > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format("File too large. Maximum size: %d bytes", maxFileSize)
            );
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("File name cannot be null");
        }

        // Détecter les tentatives de path traversal
        if (PATH_TRAVERSAL.matcher(fileName).find()) {
            throw new SecurityException("Path traversal attempt detected in filename");
        }

        // Nettoyer le nom de fichier
        String cleaned = UNSAFE_FILENAME_CHARS.matcher(fileName).replaceAll("_");

        // Limiter la longueur
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100);
        }

        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Invalid file name after sanitization");
        }

        return cleaned;
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == fileName.length() - 1) {
            throw new IllegalArgumentException("File must have a valid extension");
        }
        return fileName.substring(lastDot + 1);
    }

    private void validateFileType(String contentType, String extension) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type is required");
        }

        Set<String> allowedExtensions = ALLOWED_TYPES.get(contentType.toLowerCase());
        if (allowedExtensions == null) {
            throw new SecurityException("Content type not allowed: " + contentType);
        }

        if (!allowedExtensions.contains(extension.toLowerCase())) {
            throw new SecurityException(
                    String.format("Extension '%s' not allowed for content type '%s'", extension, contentType)
            );
        }
    }

    private void validateFileContent(java.nio.file.Path filePath, String extension) throws IOException {
        byte[] signature = FILE_SIGNATURES.get(extension.toLowerCase());
        if (signature == null) {
            return; // Pas de signature définie pour ce type
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] fileHeader = new byte[signature.length];
            int bytesRead = is.read(fileHeader);

            if (bytesRead < signature.length || !Arrays.equals(fileHeader, signature)) {
                throw new SecurityException(
                        String.format("File content does not match expected type: %s", extension)
                );
            }
        }
    }

    private java.nio.file.Path storeFileSecurely(FileUpload file, String secureFileName) throws IOException {
        java.nio.file.Path uploadDir = Paths.get(uploadBasePath);
        java.nio.file.Path targetPath = uploadDir.resolve(secureFileName);

        // Vérification finale contre path traversal
        if (!targetPath.normalize().startsWith(uploadDir.normalize())) {
            throw new SecurityException("Path traversal attempt detected");
        }

        // Copie sécurisée
        Files.copy(file.uploadedFile(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Permissions restrictives sur le fichier
        targetPath.toFile().setReadable(false, false);
        targetPath.toFile().setReadable(true, true);
        targetPath.toFile().setWritable(false, false);
        targetPath.toFile().setWritable(true, true);
        targetPath.toFile().setExecutable(false);

        return targetPath;
    }

    private Asset createAssetEntity(String id, String originalName, FileUpload file, java.nio.file.Path storedPath) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.name = originalName;
        asset.size = file.size();
        asset.contentType = file.contentType();
        asset.createdAt = Instant.now();

        // URL sécurisée - pas d'accès direct au fichier
        asset.url = "/api/assets/" + id + "/download";

        return asset;
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ROLE_TEMPLATES_ADMIN")
    public Response delete(List<Map<String, Object>> assets) {
        if (assets == null || assets.isEmpty()) {
            return Response.noContent().build();
        }

        List<String> deletedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Map<String, Object> assetData : assets) {
            try {
                String id = (String) assetData.get("id");
                if (id == null || id.isBlank()) {
                    errors.add("Missing asset ID");
                    continue;
                }

                Asset asset = Asset.findById(id);
                if (asset == null) {
                    errors.add("Asset not found: " + id);
                    continue;
                }

                // Supprimer le fichier physique
                deletePhysicalFile(asset);

                // Supprimer l'entité
                asset.delete();
                deletedIds.add(id);

                LOG.infof("Asset deleted: %s", id);

            } catch (Exception e) {
                LOG.errorf(e, "Error deleting asset: %s", assetData);
                errors.add("Failed to delete asset");
            }
        }

        Map<String, Object> response = Map.of(
                "deleted", deletedIds,
                "errors", errors,
                "summary", Map.of(
                        "requested", assets.size(),
                        "deleted", deletedIds.size(),
                        "failed", errors.size()
                )
        );

        return Response.ok(response).build();
    }

    private void deletePhysicalFile(Asset asset) {
        try {
            // Extraire le nom de fichier de l'URL
            String fileName = extractFileNameFromAsset(asset);
            if (fileName != null) {
                java.nio.file.Path filePath = Paths.get(uploadBasePath, fileName);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    LOG.debugf("Physical file deleted: %s", filePath);
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to delete physical file for asset: %s", asset.getId());
        }
    }

    private String extractFileNameFromAsset(Asset asset) {
        // Cette méthode dépend de votre stratégie de stockage
        // Vous pourriez stocker le chemin physique dans Asset
        // ou reconstruire le nom à partir de l'ID
        return asset.getId() + "_" + asset.name;
    }
}

