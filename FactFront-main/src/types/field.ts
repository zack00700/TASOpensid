export interface Field {
  key: string;
  defaultValue: string;
  translations: Record<string, string>;
  pages?: string[];
}
