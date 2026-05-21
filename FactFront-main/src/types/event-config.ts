export type EventType = 'IN' | 'OUT' | 'INTERMEDIATE';
export type EventScope = 'ITEM' | 'VESSEL' | 'BOTH';

export interface EventConfig {
    id: string;
    eventName: string;
    eventType: EventType;
    billedEvent: boolean;
    scope?: EventScope;
}
