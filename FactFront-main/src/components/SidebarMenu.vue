<template>
  <div class="relative flex shrink-0 h-full">
    <aside
      v-if="isOpen"
      class="w-64 shrink-0 border-r border-[rgba(42,36,30,0.08)] flex flex-col h-full bg-[rgba(253,250,242,0.35)] backdrop-blur-[2px]"
    >
      <!-- Mobile close button -->
      <button
        @click="emit('close')"
        class="lg:hidden absolute top-3 right-3 p-1.5 rounded-tide-pill text-tide-ink/45 hover:text-tide-ink hover:bg-[rgba(255,253,247,0.7)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tide-blue"
        :aria-label="$t('nav.closeSidebar')"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
        </svg>
      </button>

      <!-- Header with User Info -->
      <div class="p-4 border-b border-[rgba(42,36,30,0.06)]">
        <div class="flex items-center space-x-3 p-3 rounded-tide-card bg-gradient-to-br from-[rgba(255,253,247,0.95)] to-[rgba(252,247,238,0.7)] border border-[rgba(60,50,35,0.10)] shadow-glass">
          <div class="w-10 h-10 bg-gradient-to-br from-tide-blue to-tide-blue-deep rounded-full flex items-center justify-center flex-shrink-0 text-sm font-semibold text-tide-paper">
            {{ initials }}
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-semibold text-tide-ink truncate">{{ displayName }}</p>
            <p class="text-xs text-tide-ink/45 truncate font-mono">{{ displayEmail }}</p>
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
            class="flex items-center w-full px-2 py-2 text-tide-ink/40 hover:text-tide-ink/70 rounded-tide-pill transition-colors duration-200 group focus-visible:ring-2 focus-visible:ring-tide-blue focus-visible:ring-offset-1"
            :aria-expanded="section.open"
            :aria-controls="`section-${section.key}-list`"
          >
            <div
              class="flex items-center justify-center w-5 h-5 mr-2 transition-colors duration-200"
            >
              <component :is="section.icon" class="w-3.5 h-3.5" />
            </div>
            <span class="flex-1 text-[10px] font-semibold tracking-[0.12em] uppercase text-left">{{ $t(section.i18nKey) }}</span>

            <ChevronRight
              class="w-3.5 h-3.5 transition-all duration-300 opacity-50"
              :class="{ 'rotate-90 opacity-80': section.open }"
              :aria-label="t('sidebarMenu.label.toggleSection', { name: t(section.i18nKey) })"
            />
          </button>

          <!-- Menu Items -->
          <div
            :id="`section-${section.key}-list`"
            class="overflow-hidden transition-all duration-300 ease-out"
            :class="section.open ? 'max-h-[600px] opacity-100' : 'max-h-0 opacity-0'"
          >
            <ul class="mt-1 ml-2 pl-4 border-l border-[rgba(42,36,30,0.08)] space-y-0.5">
              <li v-for="item in section.items" :key="item.name">
                <button
                  @click="setActive(item)"
                  :data-active="activeItem === item.name"
                  :data-test="`menu-item-${item.name.toLowerCase().replace(/\s+/g, '-')}`"
                  :tabindex="activeItem === item.name ? -1 : 0"
                  class="flex items-start w-full px-2.5 py-2 text-left text-[13px] rounded-tide-pill group relative focus-visible:ring-2 focus-visible:ring-tide-blue focus-visible:ring-offset-1"
                  :class="
                    activeItem === item.name
                      ? 'bg-gradient-to-br from-[rgba(255,253,247,0.95)] to-[rgba(252,247,238,0.70)] text-tide-ink font-medium border border-[rgba(60,50,35,0.10)] transition-none'
                      : 'text-tide-ink/70 border border-transparent hover:bg-[rgba(255,253,247,0.45)] hover:text-tide-ink transition-all duration-150'
                  "
                >
                  <!-- Active indicator -->
                  <div
                    v-if="activeItem === item.name"
                    class="absolute left-0 top-1/2 transform -translate-y-1/2 w-[3px] h-5 bg-gradient-to-b from-tide-blue to-tide-blue-deep rounded-full -ml-[17px]"
                  ></div>

                  <div
                    class="flex shrink-0 items-center justify-center w-5 h-5 mr-2.5 mt-px"
                    :class="
                      activeItem === item.name
                        ? 'text-tide-blue-deep transition-none'
                        : 'text-tide-ink/40 group-hover:text-tide-ink/70 transition-all duration-150'
                    "
                  >
                    <component :is="item.icon" class="w-4 h-4" />
                  </div>
                  <span class="flex-1 leading-[1.35]">{{ $t(item.i18nKey) }}</span>
                </button>
              </li>
            </ul>
          </div>
        </section>
      </nav>

      <!-- Pinned logout -->
      <div class="p-3 border-t border-[rgba(42,36,30,0.06)] flex-shrink-0">
        <button
          @click="handleLogout"
          class="flex items-center w-full px-2.5 py-2 rounded-tide-pill text-tide-ink/65 hover:bg-[rgba(181,99,88,0.10)] hover:text-tide-rust-deep transition-all duration-200 group focus-visible:ring-2 focus-visible:ring-tide-rust focus-visible:ring-offset-1"
          :aria-label="$t('nav.signOut')"
        >
          <div class="flex items-center justify-center w-5 h-5 mr-2.5 text-tide-ink/40 group-hover:text-tide-rust-deep transition-colors duration-200">
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
        class="relative flex items-center justify-center w-9 h-9 rounded-full bg-[rgba(255,253,247,0.95)] backdrop-blur border border-[rgba(60,50,35,0.12)] shadow-glass transition-opacity duration-300 hover:bg-tide-paper focus-visible:ring-2 focus-visible:ring-tide-blue focus-visible:ring-offset-1"
        :class="[
          showToggle ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
        ]"
        :aria-label="isOpen ? $t('nav.closeSidebar') : $t('nav.openSidebar')"
        :aria-hidden="!showToggle"
        :tabindex="showToggle ? 0 : -1"
      >
        <ChevronLeft v-if="isOpen" class="w-5 h-5 text-tide-ink/70" />
        <ChevronRight v-else class="w-5 h-5 text-tide-ink/70" />
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
  Percent,
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
      { name: 'Taxes', i18nKey: 'nav.taxes', icon: Percent, path: '/taxes' },
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
