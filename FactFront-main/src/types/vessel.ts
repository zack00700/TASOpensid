export interface Vessel {
  id?: string;
  name: string;
  imoNumber: string;
  mmsi?: string;
  callSign: string;
  flag: string;
  owner: string;
  operator: string;
  vesselType: string;
  status: 'Active' | 'Inactive';
}