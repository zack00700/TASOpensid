export type ForecastMetric =
  | 'YARD_MOVES'
  | 'GATE_IN'
  | 'GATE_OUT'
  | 'VESSEL_CALLS'
  | 'SHIFTS_STARTED';

export interface CapacityForecast {
  metric: ForecastMetric | string;
  historicalYearMonths: string[];
  historicalValues: number[];
  forecastYearMonths: string[];
  forecastValues: number[];
  forecastLowerBound: number[];
  forecastUpperBound: number[];
  slopePerMonth: number;
  r2: number;
}
