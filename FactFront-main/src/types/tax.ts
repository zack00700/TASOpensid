export type TaxType = 'PERCENTAGE' | 'FIXED_AMOUNT';

export const TAX_TYPES: TaxType[] = ['PERCENTAGE', 'FIXED_AMOUNT'];

export interface Tax {
  id?: string;
  name: string;
  code: string;
  type: TaxType;
  rate: number;
  validFrom?: string | null;
  validTo?: string | null;
  isActive?: boolean;
}
