<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useRouter } from 'vue-router';
import { useAuthStore } from './stores/authStore';
import { useKeyboardShortcut } from './composables/useKeyboardShortcut';
import ShortcutHelp from './components/ui/ShortcutHelp.vue';
import AskAiFab from './components/AskAiFab.vue';
import FeatureRequestWizard from './components/FeatureRequestWizard.vue';
import SidebarMenu from './components/SidebarMenu.vue';
import Login from './components/Login.vue';
import LocaleSwitcher from './components/LocaleSwitcher.vue';
import logoUrl from './assets/logo1.png';
import { LogOut } from 'lucide-vue-next';

const authStore = useAuthStore();
const { isAuthenticated, loading, user } = storeToRefs(authStore);
const router = useRouter();

// Restore the deep route the user was on before the auth guard cancelled
// their initial navigation. Triggered on silent SSO restore (refresh of any
// authenticated page) — the manual Login.vue flow already handles the same
// sessionStorage key synchronously and clears it before this watcher fires.
watch(isAuthenticated, (auth) => {
  if (!auth) return;
  const saved = sessionStorage.getItem('redirectAfterLogin');
  if (!saved) return;
  sessionStorage.removeItem('redirectAfterLogin');
  if (saved !== router.currentRoute.value.fullPath) {
    router.push(saved);
  }
}, { immediate: true });

const sidebarOpen = ref(false);
const userMenuOpen = ref(false);
const userMenuRef = ref<HTMLElement | null>(null);

const displayName = computed(() => user.value?.fullName || user.value?.username || 'User');
const displayEmail = computed(() => user.value?.email || '');
const initials = computed(() => {
  const name = displayName.value;
  const parts = name.trim().split(/\s+/);
  return parts.length >= 2
    ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
    : name.slice(0, 2).toUpperCase();
});

function handleClickOutside(e: MouseEvent) {
  if (userMenuRef.value && !userMenuRef.value.contains(e.target as Node)) {
    userMenuOpen.value = false;
  }
}

useKeyboardShortcut('/', () => {
  const input = document.querySelector<HTMLInputElement>(
    'input[aria-label="Search"], input[type="search"]',
  );
  if (input) input.focus();
});

onMounted(() => {
  authStore.initialize();
  document.addEventListener('mousedown', handleClickOutside);
});

onUnmounted(() => {
  document.removeEventListener('mousedown', handleClickOutside);
});
</script>

<template>
  <!-- Loading screen -->
  <div v-if="loading" class="min-h-screen bg-gray-50 flex items-center justify-center">
    <div class="text-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
      <p class="text-gray-600">{{ $t('common.loading') }}</p>
    </div>
  </div>

  <!-- Login page -->
  <Login v-else-if="!isAuthenticated" />

  <!-- Main application (when authenticated) -->
  <div v-else class="flex min-h-[100dvh] bg-gray-50">
    <!-- Mobile overlay backdrop -->
    <transition name="fade">
      <div
        v-if="sidebarOpen"
        class="fixed inset-0 bg-black/50 z-30 lg:hidden"
        @click="sidebarOpen = false"
      />
    </transition>

    <!-- Sidebar Navigation -->
    <div :class="[
      'lg:relative lg:block lg:shrink-0',
      sidebarOpen ? 'fixed inset-0 z-40 lg:static' : 'hidden lg:block'
    ]">
      <SidebarMenu :mobile-open="sidebarOpen" @close="sidebarOpen = false" />
    </div>

    <!-- Main Content -->
    <div class="flex-1 flex flex-col min-w-0">
      <!-- Header -->
      <header class="bg-white shadow sticky top-0 z-30 h-16 flex items-center justify-between px-4 gap-2">
        <!-- Hamburger button: mobile only -->
        <button
          @click="sidebarOpen = true"
          class="lg:hidden p-2 rounded-lg text-gray-500 hover:bg-gray-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 flex-shrink-0"
          :aria-label="$t('nav.openMenu')"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"/>
          </svg>
        </button>

        <img :src="logoUrl" :alt="$t('app.label.companyLogo')" class="h-14 w-auto" />

        <!-- Locale switcher -->
        <LocaleSwitcher />

        <!-- User menu (right side — always visible) -->
        <div ref="userMenuRef" class="relative flex-shrink-0">
          <button
            @click="userMenuOpen = !userMenuOpen"
            class="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-gray-100 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            aria-haspopup="true"
            :aria-expanded="userMenuOpen"
            :aria-label="$t('nav.userMenu')"
          >
            <!-- Avatar -->
            <div class="w-8 h-8 bg-gradient-to-br from-slate-600 to-slate-800 rounded-full flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
              {{ initials }}
            </div>
            <!-- Name (hidden on very small screens) -->
            <span class="hidden sm:block text-sm font-medium text-gray-700 max-w-[120px] truncate">{{ displayName }}</span>
            <!-- Chevron -->
            <svg class="w-3.5 h-3.5 text-gray-400 flex-shrink-0 transition-transform duration-200" :class="userMenuOpen ? 'rotate-180' : ''" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
            </svg>
          </button>

          <!-- Dropdown -->
          <transition
            enter-active-class="transition ease-out duration-100"
            enter-from-class="transform opacity-0 scale-95"
            enter-to-class="transform opacity-100 scale-100"
            leave-active-class="transition ease-in duration-75"
            leave-from-class="transform opacity-100 scale-100"
            leave-to-class="transform opacity-0 scale-95"
          >
            <div
              v-if="userMenuOpen"
              class="absolute right-0 top-full mt-2 w-56 bg-white rounded-xl shadow-lg border border-gray-100 py-1 z-50 origin-top-right"
              role="menu"
            >
              <!-- User info -->
              <div class="px-4 py-3 border-b border-gray-100">
                <p class="text-sm font-semibold text-gray-900 truncate">{{ displayName }}</p>
                <p class="text-xs text-gray-500 truncate mt-0.5">{{ displayEmail }}</p>
              </div>

              <!-- Logout -->
              <button
                @click="authStore.logout()"
                class="flex items-center w-full px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors gap-2.5 mt-1"
                role="menuitem"
              >
                <LogOut class="w-4 h-4 flex-shrink-0" />
                {{ $t('nav.signOut') }}
              </button>
            </div>
          </transition>
        </div>
      </header>

      <main class="flex-1 min-w-0 overflow-y-auto overflow-x-hidden px-4 py-6">
        <router-view />
      </main>
    </div>
  </div>


  <!-- Teleported FAB: only when authenticated and not loading -->
  <teleport to="body" v-if="!loading && isAuthenticated">
    <AskAiFab />
  </teleport>

  <!-- Feature Request floating wizard: available to all authenticated users -->
  <teleport to="body" v-if="!loading && isAuthenticated">
    <FeatureRequestWizard />
  </teleport>

  <!-- Keyboard shortcut help (? to toggle) -->
  <ShortcutHelp v-if="!loading && isAuthenticated" />




</template>

<style scoped>
header { transition: none; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
