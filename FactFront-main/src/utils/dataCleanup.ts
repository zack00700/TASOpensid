/**
 * Utility functions to clean data before sending to backend
 * Fixes Jackson date parsing issues
 */

export const cleanBillOfLadingData = (data: any): any => {
    if (!data || typeof data !== 'object') return data;

    const cleaned = Array.isArray(data) ? [...data] : { ...data };

    // If it's an array, clean each item
    if (Array.isArray(cleaned)) {
        return cleaned.map(item => cleanBillOfLadingData(item));
    }

    // Remove server-controlled timestamp fields that should not be sent
    delete cleaned.createdAt;
    delete cleaned.updatedAt;
    delete cleaned.id; // Let server generate IDs for new records

    // Clean date fields recursively
    Object.keys(cleaned).forEach(key => {
        const value = cleaned[key];

        // Handle date-only strings (YYYY-MM-DD format)
        if (typeof value === 'string' &&
            value.length === 10 &&
            /^\d{4}-\d{2}-\d{2}$/.test(value)) {
            // Convert to full ISO timestamp
            cleaned[key] = value + 'T00:00:00.000Z';
        }

        // Handle datetime strings that might be missing timezone
        else if (typeof value === 'string' &&
            /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(value)) {
            // Add timezone if missing
            cleaned[key] = value + '.000Z';
        }

        // Recursively clean nested objects and arrays
        else if (typeof value === 'object' && value !== null) {
            cleaned[key] = cleanBillOfLadingData(value);
        }
    });

    return cleaned;
};
/**
* Clean form data specifically for BillOfLading forms
*/
export const cleanFormData = (formData: any) => {
    if (!formData || Object.keys(formData).length === 0) {
        return formData;
    }

    return cleanBillOfLadingData(formData);
};

/**
 * Validate and clean JSON import data
 */
export const cleanImportData = (jsonData: any[]): any[] => {
    if (!Array.isArray(jsonData)) {
        throw new Error('Import data must be an array');
    }

    return jsonData.map(item => {
        if (!item || typeof item !== 'object') {
            throw new Error('Each item in import data must be an object');
        }

        return cleanBillOfLadingData(item);
    });
};
