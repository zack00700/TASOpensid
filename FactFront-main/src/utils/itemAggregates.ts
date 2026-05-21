import type { Item, Event as ItemEvent } from '../types/item';

/** Days since gateInDate. Uses gateOutDate when present (historical dwell), else today. Null pre-gate-in. */
export function getDwellDays(item: Item): number | null {
  if (!item.gateInDate) return null;
  const end = item.gateOutDate ? new Date(item.gateOutDate) : new Date();
  const start = new Date(item.gateInDate);
  return Math.floor((end.getTime() - start.getTime()) / 86_400_000);
}

/** Tailwind class for the dwell-time cell, escalating with severity. */
export function dwellColorClass(days: number): string {
  if (days >= 14) return 'text-red-600 font-bold';
  if (days >= 7) return 'text-amber-600 font-medium';
  return 'text-slate-700';
}

export type ChargingState = { state: 'free' | 'grace' | 'past'; days?: number };

/** Free | In grace | Past grace +Nd | null when no chargingStartDate or no grace expiry past start. */
export function getChargingStatus(item: Item): ChargingState | null {
  if (!item.chargingStartDate) return null;
  const today = Date.now();
  const start = new Date(item.chargingStartDate).getTime();
  const expiry = item.gracePeriodExpiryDate ? new Date(item.gracePeriodExpiryDate).getTime() : null;
  if (today < start) return { state: 'free' };
  if (expiry !== null && today <= expiry) return { state: 'grace' };
  if (expiry !== null) return { state: 'past', days: Math.floor((today - expiry) / 86_400_000) };
  return null;
}

/** The most recent event from the active (or first) lifecycle. */
export function getLastEvent(item: Item): ItemEvent | null {
  const active = item.lifeCycles?.find((l) => l.status === 'In Progress') ?? item.lifeCycles?.[0];
  if (!active?.events?.length) return null;
  return active.events[active.events.length - 1];
}

/** "now" / "5m ago" / "2h ago" / "3d ago" / locale date string for older. */
export function formatRelativeDate(iso: string): string {
  const ts = new Date(iso).getTime();
  if (Number.isNaN(ts)) return '—';
  const diff = Date.now() - ts;
  if (diff < 60_000) return 'now';
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
  if (diff < 7 * 86_400_000) return `${Math.floor(diff / 86_400_000)}d ago`;
  return new Date(ts).toLocaleDateString();
}
