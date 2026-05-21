<template>
  <div class="relative flex shrink-0 h-full">
    <aside
      v-if="isOpen"
      class="w-64 shrink-0 border-r border-slate-600/30 flex flex-col h-full shadow-xl backdrop-blur-sm bg-gradient-to-b from-slate-800 to-slate-900"
    >
      <!-- Mobile close button -->
      <button
        @click="emit('close')"
        class="lg:hidden absolute top-3 right-3 p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-400"
        :aria-label="$t('nav.closeSidebar')"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
        </svg>
      </button>

      <!-- Header with User Info -->
      <div class="p-4 border-b border-slate-600/30">
        <div class="flex items-center space-x-3 p-3 rounded-xl bg-slate-700/20 border border-slate-600/20 shadow-sm">
          <div class="w-10 h-10 bg-gradient-to-br from-slate-300 to-slate-200 rounded-full flex items-center justify-center shadow-md flex-shrink-0 text-sm font-bold text-slate-700">
            {{ initials }}
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-semibold text-white truncate">{{ displayName }}</p>
            <p class="text-xs text-slate-400 truncate">{{ displayEmail }}</p>
          </div>
        </div>
      </div>

      <!-- Sidebar Navigation -->
      <nav class="flex-1 overflow-y-auto py-4 px-3 space-y-1 min-h-0">
        <section
          v-for="section in sections"
          :key="section.key"
          class="mb-6 last:mb-0"
        >
          <button
            :data-test="`section-${section.key}`"
            @click="toggleSection(section)"
            class="flex items-center w-full px-3 py-3 text-slate-200 hover:bg-slate-600/30 rounded-xl transition-all duration-200 group focus-visible:ring-2 focus-visible:ring-slate-400 focus-visible:ring-offset-1 focus-visible:ring-offset-slate-900"
            :aria-expanded="section.open"
            :aria-controls="`section-${section.key}-list`"
          >
            <div
              class="flex items-center justify-center w-9 h-9 rounded-lg bg-slate-600/20 group-hover:bg-slate-500/30 border border-slate-500/20 transition-all duration-200 mr-3"
            >
              <component :is="section.icon" class="w-4 h-4 text-slate-300" />
            </div>
            <span class="flex-1 text-sm font-medium text-left text-white">{{ $t(section.i18nKey) }}</span>

            <ChevronRight
              class="w-4 h-4 transition-all duration-300 text-slate-400"
              :class="{ 'rotate-90 text-slate-200': section.open }"
              :aria-label="t('sidebarMenu.label.toggleSection', { name: t(section.i18nKey) })"
            />
          </button>

          <!-- Menu Items -->
          <div
            :id="`section-${section.key}-list`"
            class="overflow-hidden transition-all duration-300 ease-out"
            :class="section.open ? 'max-h-[600px] opacity-100' : 'max-h-0 opacity-0'"
          >
            <ul class="mt-2 ml-3 pl-6 border-l border-slate-600/30 space-y-1">
              <li v-for="item in section.items" :key="item.name">
                <button
                  @click="setActive(item)"
                  :data-active="activeItem === item.name"
                  :data-test="`menu-item-${item.name.toLowerCase().replace(/\s+/g, '-')}`"
                  :tabindex="activeItem === item.name ? -1 : 0"
                  class="flex items-center w-full px-3 py-2.5 text-sm rounded-lg group relative focus-visible:ring-2 focus-visible:ring-slate-400 focus-visible:ring-offset-1 focus-visible:ring-offset-slate-900"
                  :class="
                    activeItem === item.name
                      ? 'bg-gradient-to-r from-slate-500/20 to-slate-400/20 text-white shadow-sm border border-slate-400/30 backdrop-blur-sm transition-none'
                      : 'text-slate-300 hover:bg-slate-600/20 hover:text-white transition-all duration-150'
                  "
                >
                  <!-- Active indicator -->
                  <div
                    v-if="activeItem === item.name"
                    class="absolute left-0 top-1/2 transform -translate-y-1/2 w-1 h-6 bg-gradient-to-b from-slate-300 to-slate-400 rounded-full -ml-3 shadow-sm"
                  ></div>

                  <div
                    class="flex items-center justify-center w-7 h-7 rounded-md mr-3"
                    :class="
                      activeItem === item.name
                        ? 'bg-slate-400/20 text-white border border-slate-400/20 transition-none'
                        : 'text-slate-400 group-hover:text-white group-hover:bg-slate-600/20 transition-all duration-150'
                    "
                  >
                    <component :is="item.icon" class="w-4 h-4" />
                  </div>
                  <span class="font-medium">{{ $t(item.i18nKey) }}</span>
                </button>
              </li>
            </ul>
          </div>
        </section>
      </nav>

      <!-- Pinned logout -->
      <div class="p-3 border-t border-slate-600/30 flex-shrink-0">
        <button
          @click="handleLogout"
          class="flex items-center w-full px-3 py-2.5 rounded-xl text-slate-300 hover:bg-red-500/15 hover:text-red-300 transition-all duration-200 group focus-visible:ring-2 focus-visible:ring-red-400 focus-visible:ring-offset-1 focus-visible:ring-offset-slate-900"
          :aria-label="$t('nav.signOut')"
        >
          <div class="flex items-center justify-center w-7 h-7 rounded-md mr-3 text-slate-400 group-hover:text-red-300 transition-colors duration-200">
            <LogOut class="w-4 h-4" />
          </div>
          <span class="text-sm font-medium">{{ $t('nav.signOut') }}</span>
        </button>
      </div>
    </aside>

    <!-- Minimal, centered, auto-hiding Toggle (desktop only) -->
    <div
      class="hidden lg:block fixed top-1/2 -translate-y-1/2 z-50 transition-all duration-300"
      :class="isOpen ? 'left-[256px]' : 'left-3'"
      @mouseenter="showToggle = true"
      @mouseleave="scheduleHide()"
    >
      <button
        @click="isOpen = !isOpen; scheduleHide(true)"
        class="relative flex items-center justify-center w-9 h-9 rounded-full bg-white/95 backdrop-blur border border-slate-200/60 shadow-lg transition-opacity duration-300 hover:bg-white hover:shadow-xl focus-visible:ring-2 focus-visible:ring-slate-400 focus-visible:ring-offset-1 focus-visible:ring-offset-slate-900"
        :class="[
          showToggle ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
        ]"
        :aria-label="isOpen ? $t('nav.closeSidebar') : $t('nav.openSidebar')"
        :aria-hidden="!showToggle"
        :tabindex="showToggle ? 0 : -1"
      >
        <ChevronLeft v-if="isOpen" class="w-5 h-5 text-slate-700" />
        <ChevronRight v-else class="w-5 h-5 text-slate-700" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter, useRoute } from 'vue-router';
import { storeToRefs } from 'pinia';
import { useAuthStore } from '../stores/authStore';
import {
  FileText,
  Package,
  Settings as SettingsIcon,
  Ship,
  ClipboardList,
  Users,
  File,
  LayoutTemplate,
  User,
  ChevronRight,
  ChevronLeft,
  Briefcase,
  Cog,
  Languages,
  LogOut,
  Hash,
  Tag,
  Radio,
  CreditCard,
  TrendingUp,
  Sliders,
  Lightbulb,
  BarChart3,
  ShieldCheck,
  Container,
  Layers,
} from 'lucide-vue-next';

const { t } = useI18n();
const authStore = useAuthStore();
const { user } = storeToRefs(authStore);

const displayName = computed(() => user.value?.fullName || user.value?.username || 'User');
const displayEmail = computed(() => user.value?.email || '');
const initials = computed(() => {
  const name = displayName.value;
  const parts = name.trim().split(/\s+/);
  return parts.length >= 2
    ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
    : name.slice(0, 2).toUpperCase();
});

async function handleLogout() {
  await authStore.logout();
}

interface MenuItem {
  name: string;
  i18nKey: string;
  icon: any;
  path: string;
}

interface MenuSection {
  key: string;
  title: string;
  i18nKey: string;
  icon: any;
  items: MenuItem[];
  open: boolean;
}

const props = defineProps<{
  operationsIcon?: any;
  configurationIcon?: any;
  mobileOpen?: boolean;
}>();

const emit = defineEmits<{ (e: 'close'): void }>();

const sections = ref<MenuSection[]>([
  {
    key: 'operations',
    title: 'Operations',
    i18nKey: 'nav.section.operations',
    icon: props.operationsIcon || Briefcase,
    open: true,
    items: [
      { name: 'Invoices', i18nKey: 'nav.invoices', icon: FileText, path: '/invoices' },
      { name: 'Items', i18nKey: 'nav.items', icon: Package, path: '/items' },
      { name: 'Vessel Visits', i18nKey: 'nav.vesselVisits', icon: Ship, path: '/vessels' },
      { name: 'Bill of Lading', i18nKey: 'nav.billOfLading', icon: ClipboardList, path: '/bills' },
      { name: 'EDI Messages', i18nKey: 'nav.ediMessages', icon: Radio, path: '/edi' },
      { name: 'Customs', i18nKey: 'nav.customs', icon: ShieldCheck, path: '/customs' },
      { name: 'Payments', i18nKey: 'nav.payments', icon: CreditCard, path: '/payments' },
    ],
  },
  {
    key: 'dd',
    title: 'D&D',
    i18nKey: 'nav.section.dd',
    icon: TrendingUp,
    open: false,
    items: [
      { name: 'D&D Dashboard', i18nKey: 'nav.ddDashboard', icon: TrendingUp, path: '/dd' },
      { name: 'D&D Rules', i18nKey: 'nav.ddRules', icon: Sliders, path: '/dd/rules' },
    ],
  },
  {
    key: 'analytics',
    title: 'Analytics',
    i18nKey: 'nav.section.analytics',
    icon: BarChart3,
    open: false,
    items: [
      { name: 'Capacity Forecast', i18nKey: 'nav.capacityForecast', icon: TrendingUp, path: '/forecasting' },
    ],
  },
  {
    key: 'configuration',
    title: 'Configuration',
    i18nKey: 'nav.section.configuration',
    icon: props.configurationIcon || Cog,
    open: false,
    items: [
      { name: 'Event Config', i18nKey: 'nav.eventConfig', icon: SettingsIcon, path: '/events-config' },
      { name: 'ISO Codes', i18nKey: 'nav.isoCodes', icon: Container, path: '/iso-codes' },
      { name: 'Container Archetypes', i18nKey: 'nav.containerArchetypes', icon: Layers, path: '/archetypes' },
      { name: 'Vessels', i18nKey: 'nav.vessels', icon: Ship, path: '/vessel-registry' },
      { name: 'Third Parties', i18nKey: 'nav.thirdParties', icon: Users, path: '/third-parties' },
      { name: 'Application Users', i18nKey: 'nav.applicationUsers', icon: User, path: '/admin/users' },
      { name: 'Contracts', i18nKey: 'nav.contracts', icon: File, path: '/contracts' },
      { name: 'Tariffs', i18nKey: 'nav.tariffs', icon: Tag, path: '/tariffs' },
      { name: 'Template Designer', i18nKey: 'nav.templateDesigner', icon: LayoutTemplate, path: '/template-designer' },
      { name: 'Translations', i18nKey: 'nav.translations', icon: Languages, path: '/i18n' },
      { name: 'Invoice Sequences', i18nKey: 'nav.invoiceSequences', icon: Hash, path: '/configuration/sequences' },
      { name: 'FR Dashboard', i18nKey: 'nav.frDashboard', icon: TrendingUp, path: '/fr-dashboard' },
      { name: 'Feature Requests', i18nKey: 'nav.featureRequests', icon: Lightbulb, path: '/backlog' },
    ],
  },
]);

const STORAGE_KEY = 'sidebar:v1';

const activeItem = ref('');

const router = useRouter();
const route = useRoute();

// Function to find menu item name by path
const findItemNameByPath = (path: string): string => {
  for (const section of sections.value) {
    const item = section.items.find((item) => path.startsWith(item.path));
    if (item) return item.name;
  }
  return '';
};

// Set initial active item based on current route
onMounted(() => {
  activeItem.value = findItemNameByPath(route.path);

  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      sections.value.forEach((section) => {
        if (typeof parsed[section.key] === 'boolean') {
          section.open = parsed[section.key];
        }
      });
    } catch {
      /* ignore malformed */
    }
  }

  // Auto-hide initialization
  scheduleHide();
  window.addEventListener('mousemove', handleMouseMove, { passive: true });
  window.addEventListener('keydown', handleKeydown, { passive: true });
});

onUnmounted(() => {
  if (hideTimer) window.clearTimeout(hideTimer);
  window.removeEventListener('mousemove', handleMouseMove);
  window.removeEventListener('keydown', handleKeydown);
});

// Watch route for active item changes and close mobile drawer on navigation
watch(
  () => route.path,
  (newPath) => {
    activeItem.value = findItemNameByPath(newPath);
    emit('close');
  }
);

// Watch sections for localStorage persistence
watch(
  sections,
  (newVal) => {
    const state: Record<string, boolean> = {};
    newVal.forEach((sec) => (state[sec.key] = sec.open));
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  },
  { deep: true }
);

function toggleSection(section: MenuSection) {
  section.open = !section.open;
}

function setActive(item: MenuItem) {
  // Remove focus from any currently focused element to prevent yellow border
  if (document.activeElement && document.activeElement instanceof HTMLElement) {
    document.activeElement.blur();
  }

  // Force blur on all sidebar buttons to ensure no focus states remain
  const sidebarButtons = document.querySelectorAll('[data-test^="menu-item-"]');
  sidebarButtons.forEach(button => {
    if (button instanceof HTMLElement) {
      button.blur();
    }
  });

  // Immediately update active state for instant visual feedback
  activeItem.value = item.name;
  // Then navigate (router.push is async but visual state is already updated)
  router.push(item.path);
}

const isOpen = ref(true);

// --- Auto-hide toggle logic ---
const showToggle = ref(true);
let hideTimer: number | null = null;

// Hide after a short delay; immediate=true hides sooner after click
function scheduleHide(immediate = false) {
  if (hideTimer) {
    window.clearTimeout(hideTimer);
    hideTimer = null;
  }
  hideTimer = window.setTimeout(() => (showToggle.value = false), immediate ? 900 : 1500);
}

// Show when the cursor nears the left edge (or the sidebar edge if open)
function handleMouseMove(e: MouseEvent) {
  const threshold = isOpen.value ? 272 : 24; // a bit past the sidebar edge (w-64 = 256px) / near screen edge
  if (e.clientX <= threshold) {
    if (!showToggle.value) showToggle.value = true;
    scheduleHide();
  }
}

// Keep visible while the button is focused (keyboard users)
function handleKeydown() {
  showToggle.value = true;
  scheduleHide();
}
</script>

<style scoped>
/* No extra styles needed; Tailwind handles visuals */
</style>
