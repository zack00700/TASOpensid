package fr.alb.billing.service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import fr.alb.billing.model.InvoiceTemplate;  // Import ajouté
import io.quarkus.logging.Log;

@ApplicationScoped
public class TemplateMigrationService {

    /**
     * Migre les templates existants utilisant org.bson.Document vers la nouvelle structure
     * À utiliser uniquement lors de la migration des données existantes
     */
    @Transactional
    public void migrateExistingTemplates() {
        Log.info("Starting template migration...");

        List<InvoiceTemplate> templates = InvoiceTemplate.listAll();
        int migrated = 0;

        for (InvoiceTemplate template : templates) {
            boolean needsMigration = false;

            // Migration des bindings si nécessaire
            if (template.bindings == null) {
                template.bindings = new HashMap<>();
                needsMigration = true;
            }

            // Migration des pageSettings si nécessaire
            if (template.pageSettings == null) {
                template.pageSettings = new InvoiceTemplate.PageSettings();
                needsMigration = true;
            }

            // Migration du template content si nécessaire
            if (template.template == null) {
                template.template = new InvoiceTemplate.TemplateContent();
                needsMigration = true;
            }

            // Migration des anciennes données Document vers les nouvelles structures
            if (needsMigration) {
                // Conversion des anciennes données si elles existent
                migrateLegacyData(template);

                template.updateTimestamp();
                template.update();
                migrated++;

                Log.infof("Migrated template: %s (ID: %s)", template.name, template.id);
            }
        }

        Log.infof("Template migration completed. Migrated %d templates.", migrated);
    }

    private void migrateLegacyData(InvoiceTemplate template) {
        // Si vous avez des données legacy à migrer depuis des champs Document,
        // implémentez la logique ici

        // Exemple de migration de données legacy
        /*
        if (template.legacyBindings instanceof Document) {
            Document doc = (Document) template.legacyBindings;
            template.bindings = new HashMap<>();
            for (String key : doc.keySet()) {
                template.bindings.put(key, doc.get(key));
            }
        }
        */
    }

    /**
     * Valide que tous les templates ont la structure correcte
     * Read-only operation - no transaction needed as it only reads and validates data.
     */
    public void validateTemplateStructure() {
        List<InvoiceTemplate> templates = InvoiceTemplate.listAll();
        int invalidCount = 0;

        for (InvoiceTemplate template : templates) {
            if (!template.isValid()) {
                Log.warnf("Invalid template found: %s (ID: %s)", template.name, template.id);
                invalidCount++;
            }
        }

        if (invalidCount > 0) {
            Log.warnf("Found %d invalid templates. Consider running migration.", invalidCount);
        } else {
            Log.info("All templates have valid structure.");
        }
    }
}