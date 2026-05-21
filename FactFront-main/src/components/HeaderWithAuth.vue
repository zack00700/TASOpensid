<script setup lang="ts">
import { ref } from 'vue';
import { LogOut, User, ChevronDown } from 'lucide-vue-next';
import { useAuthStore } from '../stores/authStore';

const authStore = useAuthStore();
const user = authStore.user;
const logout = authStore.logout;
const showUserMenu = ref(false);

function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value;
}

function closeUserMenu() {
  showUserMenu.value = false;
}

async function handleLogout() {
  closeUserMenu();
  await logout();
}

// Fermer le menu si on clique ailleurs
function handleClickOutside(event: Event) {
  const target = event.target as HTMLElement;
  if (!target.closest('.user-menu-container')) {
    closeUserMenu();
  }
}

// Ajouter/retirer l'écouteur de clic global
function setupClickOutside() {
  if (showUserMenu.value) {
    document.addEventListener('click', handleClickOutside);
  } else {
    document.removeEventListener('click', handleClickOutside);
  }
}

// Observer les changements du menu
watch(() => showUserMenu.value, setupClickOutside);

// Nettoyer à la destruction du composant
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
});
</script>

<template>
  <header class="bg-white shadow sticky