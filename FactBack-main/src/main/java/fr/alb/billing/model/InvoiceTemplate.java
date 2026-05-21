package fr.alb.billing.model;

import fr.alb.model.EntityBase;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.bson.codecs.pojo.annotations.BsonId;

@MongoEntity(collection = "INVOICE_TEMPLATE")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvoiceTemplate extends PanacheMongoEntityBase {

    @BsonId
    @Schema(description = "Unique template identifier")
    public String id; // String ID pour vos données existantes UUID

    @Schema(description = "Template name", required = true)
    public String name;

    @Schema(description = "Template status", enumeration = {"active", "archived"})
    public String status = "active";

    @Schema(description = "Template type — which invoice status it renders ('draft' or 'final')",
            enumeration = {"draft", "final"})
    public String type = "final";

    @Schema(description = "Template bindings as key-value pairs")
    public Map<String, Object> bindings;

    @Schema(description = "Page configuration settings")
    public PageSettings pageSettings;

    @Schema(description = "Template content including HTML, CSS and GrapesJS project")
    public TemplateContent template;

    @Schema(description = "Adobe Express design ID (legacy)")
    public String expressDesignId;

    @Schema(description = "Template creation timestamp")
    public Instant createdAt;

    @Schema(description = "Template last update timestamp")
    public Instant updatedAt;

    @Schema(description = "Template version number")
    public Integer version = 1;

    // Classes internes pour structurer les données
    @Schema(description = "Page settings for template rendering")
    public static class PageSettings {
        @Schema(description = "Page size", example = "A4", enumeration = {"A4", "A3", "Letter", "Legal"})
        public String size = "A4";

        @Schema(description = "Page margins in pixels", example = "40")
        public Integer margins = 40;

        @Schema(description = "Page orientation", enumeration = {"portrait", "landscape"})
        public String orientation = "portrait";

        @Schema(description = "Custom page width in pixels")
        public Integer customWidth;

        @Schema(description = "Custom page height in pixels")
        public Integer customHeight;

        // Constructeurs
        public PageSettings() {}

        public PageSettings(String size, Integer margins) {
            this.size = size;
            this.margins = margins;
        }

        public PageSettings(String size, Integer margins, String orientation) {
            this.size = size;
            this.margins = margins;
            this.orientation = orientation;
        }
    }

    @Schema(description = "Template content structure")
    public static class TemplateContent {
        @Schema(description = "Rendered HTML content")
        public String html;

        @Schema(description = "CSS styles for the template")
        public String css;

        @Schema(description = "Complete GrapesJS Studio project data")
        public Map<String, Object> studioProject = new HashMap<>();

        @Schema(description = "Template components structure")
        public String components;

        @Schema(description = "Template styles structure")
        public String styles;

        // Constructeurs
        public TemplateContent() {}

        public TemplateContent(String html, String css) {
            this.html = html;
            this.css = css;
        }

        public TemplateContent(String html, String css, Map<String, Object> studioProject) {
            this.html = html;
            this.css = css;
            this.studioProject = studioProject != null ? studioProject : new HashMap<>();
        }
    }

    // Constructeurs
    public InvoiceTemplate() {
        // Generate ID if not already set (e.g., during deserialization)
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
        this.bindings = new HashMap<>();
        this.pageSettings = new PageSettings();
        this.template = new TemplateContent();
        this.name = "";
    }

    public InvoiceTemplate(String name) {
        this();
        this.name = name != null ? name : "";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // Méthodes utilitaires pour gérer les données existantes
    public void initializeEmptyFields() {
        if (this.bindings == null) this.bindings = new HashMap<>();
        if (this.pageSettings == null) this.pageSettings = new PageSettings();
        if (this.template == null) this.template = new TemplateContent();
        if (this.name == null) this.name = "";
        if (this.status == null) this.status = "active";
        if (this.version == null) this.version = 1;
    }

    // Méthodes utilitaires
    public void updateTimestamp() {
        this.updatedAt = Instant.now();
        this.version = (this.version != null ? this.version : 0) + 1;
    }

    public boolean isActive() {
        return "active".equals(this.status);
    }

    public boolean hasContent() {
        return this.template != null &&
                (this.template.html != null ||
                        (this.template.studioProject != null && !this.template.studioProject.isEmpty()));
    }

    // Méthodes de validation
    public boolean isValid() {
        return this.name != null && !this.name.trim().isEmpty();
    }

    @Override
    public void persist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.updatedAt = Instant.now();
        this.initializeEmptyFields();
        super.persist();
    }

    @Override
    public void update() {
        updateTimestamp();
        this.initializeEmptyFields();
        super.update();
    }

    // Méthode statique pour trouver avec initialisation des champs vides
    public static InvoiceTemplate findByIdWithInit(String id) {
        InvoiceTemplate template = findById(id);
        if (template != null) {
            template.initializeEmptyFields();
        }
        return template;
    }
}