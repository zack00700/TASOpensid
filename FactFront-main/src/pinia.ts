export function defineStore(_id: string, options: any) {
  return () => {
    const state = options.state ? options.state() : {};
    const store: any = { ...state };
    const actions = options.actions || {};
    Object.entries(actions).forEach(([k, fn]) => {
      store[k] = (fn as Function).bind(store);
    });
    return store;
  };
}

export function createPinia() {
  return {};
}

export function setActivePinia(_pinia: any) {
  // no-op for stub
}
