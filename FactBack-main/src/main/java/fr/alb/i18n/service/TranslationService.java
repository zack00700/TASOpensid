package fr.alb.i18n.service;

import fr.alb.i18n.model.Translation;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@ApplicationScoped
public class TranslationService {

    /** Returns the flat map of translations for the locale, sorted by key. */
    public Map<String, String> mapForLocale(String locale) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Translation t : Translation.<Translation>list("locale", locale)) {
            sorted.put(t.key, t.value != null ? t.value : "");
        }
        return sorted;
    }

    /** Returns max(updatedAt) across translations for the locale, or null if empty. */
    public Instant versionFor(String locale) {
        return Translation.<Translation>list("locale", locale).stream()
                .map(t -> t.updatedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
    }

    /**
     * Upserts one translation. Empty/null value → deletes the doc instead and returns null.
     */
    public Translation upsert(String locale, String key, String value, String updatedBy) {
        if (value == null || value.isEmpty()) {
            delete(locale, key);
            return null;
        }
        String id = locale + ":" + key;
        Translation existing = Translation.findById(id);
        if (existing == null) {
            Translation t = new Translation(locale, key, value, updatedBy);
            t.persist();
            return t;
        }
        existing.value = value;
        existing.updatedBy = updatedBy;
        existing.update();
        return existing;
    }

    /** Deletes one translation. No-op (returns false) if absent. */
    public boolean delete(String locale, String key) {
        Translation existing = Translation.findById(locale + ":" + key);
        if (existing == null) return false;
        existing.delete();
        return true;
    }

    /** Bulk upsert. Returns the number of entries processed. */
    public int bulkImport(String locale, Map<String, String> entries, String updatedBy) {
        int touched = 0;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            upsert(locale, e.getKey(), e.getValue(), updatedBy);
            touched++;
        }
        return touched;
    }
}
