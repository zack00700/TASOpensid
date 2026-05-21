

import api from '../plugin/axios';
import { Event, Item } from '../types/item';

enum EventTypeEnum {
  IN = 'IN',
  OUT = 'OUT',
  INTERMEDIATE = 'INTERMEDIATE'
}

export class EventHandler {
  private static instance: EventHandler;

  private constructor() {}

  public static getInstance(): EventHandler {
    if (!EventHandler.instance) {
      EventHandler.instance = new EventHandler();
    }
    return EventHandler.instance;
  }

  public processEvent(event: Event, item: Item): Promise<string> {
    const eventType = (event.eventType || '').toUpperCase() as EventTypeEnum;
    switch (eventType) {
      case EventTypeEnum.IN:
        return this.handleInEvent(eventType, event, item);
      case EventTypeEnum.OUT:
        return this.handleOutEvent(eventType, event, item);
      case EventTypeEnum.INTERMEDIATE:
        return this.handleIntermediateEvent(eventType, event, item);
      default:
        throw new Error(`Invalid event type: ${event.eventType}`);
    }
  }


  private async handleInEvent(eventType: EventTypeEnum, event: Event, item: Item): Promise<string> {
    const eventToAdd = {
      type: eventType,
      timeStamp: event.timestamp,
      location: event.location,
      notes: event.notes
    };

    try {
      await api.put(`/items/${item.id}`, eventToAdd);
      return 'success';
    } catch (e) {
      console.error(`[EventHandler] Failed to handle IN event for item ${item.id}:`, e);
      throw e;
    }
  }

  private async handleOutEvent(eventType: EventTypeEnum, event: Event, item: Item): Promise<string> {
    const eventToAdd = {
      type: eventType,
      timeStamp: event.timestamp,
      location: event.location,
      notes: event.notes
    };

    try {
      await api.put(`/items/${item.id}`, eventToAdd);
      return 'success';
    } catch (e) {
      console.error(`[EventHandler] Failed to handle OUT event for item ${item.id}:`, e);
      throw e;
    }
  }

  private async handleIntermediateEvent(eventType: EventTypeEnum, event: Event, item: Item): Promise<string> {
    const eventToAdd = {
      type: eventType,
      timeStamp: event.timestamp,
      location: event.location,
      notes: event.notes
    };

    try {
      await api.put(`/items/${item.id}`, eventToAdd);
      return 'success';
    } catch (e) {
      console.error(`[EventHandler] Failed to handle INTERMEDIATE event for item ${item.id}:`, e);
      throw e;
    }
  }
}
