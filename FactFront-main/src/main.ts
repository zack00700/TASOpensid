import { createApp } from 'vue';
import { createPinia } from 'pinia';
import './index.css';
import axios from './plugin/axios';
import router from './router';
import './plugin/appInsights'; // initialise Application Insights as early as possible
import { i18n } from './i18n';

// Auto-close native date / datetime-local pickers when the user picks a value.
// Some browsers keep the popup open after selection; blurring the input on
// the `change` event forces it to close. `capture: true` ensures we run even
// if a child handler stops propagation.
document.addEventListener('change', (e) => {
  const target = e.target as HTMLInputElement | null;
  if (!target || target.tagName !== 'INPUT') return;
  if (target.type !== 'date' && target.type !== 'datetime-local') return;
  target.blur();
}, { capture: true });

// Create Pinia and app synchronously — mount immediately so the
// loading spinner is visible before any async MSAL/network work begins.
const pinia = createPinia();

import('./App.vue').then(({ default: App }) => {
  const app = createApp(App);
  app.use(pinia);
  app.use(i18n);
  app.provide('$axios', axios);
  app.use(router);
  app.mount('#app');
}).catch(error => {
  console.error('Failed to load App.vue:', error);
  document.getElementById('app')!.innerHTML = `
    <div style="font-family:sans-serif;padding:2rem;color:#dc2626;">
      <h2>Application failed to load</h2>
      <pre style="background:#fee2e2;padding:1rem;border-radius:4px;white-space:pre-wrap;">${error?.message || String(error)}</pre>
      <p style="color:#6b7280;font-size:0.875rem;">Check browser console for details.</p>
    </div>`;
});
