export type IsoTypeGroup = 'G' | 'R' | 'H' | 'U' | 'T' | 'P' | 'V' | 'B' | 'S';

export interface IsoContainerCode {
  id?: string;
  code: string;
  description: string;
  lengthFt: number;
  heightFt: number;
  typeGroup: IsoTypeGroup;
  isReefer: boolean;
  isHazmatCapable: boolean;
  isTank: boolean;
  isOpenTop: boolean;
  isStandard: boolean;
  isActive: boolean;
  archetypeId: string | null;
  tareKg: number | null;
  maxPayloadKg: number | null;
  maxGrossKg: number | null;
}
