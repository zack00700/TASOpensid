export type FilterTarget = 'ITEM' | 'BILL_OF_LADING';
export type ValueType = 'STRING' | 'INT' | 'DATE';
export type FilterOp = 'EQ' | 'LT' | 'GT' | 'BETWEEN';

export interface CalcFilter {
  target: FilterTarget;
  field: string;
  valueType: ValueType;
  op: FilterOp;
  value: string;
  valueTo?: string;
  includeNull?: boolean;
}
