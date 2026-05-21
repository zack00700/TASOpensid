<script setup lang="ts">
import { ref, reactive } from 'vue';
import { Eye, EyeOff, Lock, User, AlertCircle } from 'lucide-vue-next';
import { useAuthStore } from '../stores/authStore';
import { useRouter } from 'vue-router';

// Use authStore directly
const authStore = useAuthStore();
const router = useRouter();

// Expose store state used by the template
const loading = authStore.loading;
const error = authStore.error;
const clearError = authStore.clearError;

// État du formulaire
const form = reactive({
  username: '',
  password: ''
});

// États de l'interface
const showPassword = ref(false);

// Validation
const errors = reactive({
  username: '',
  password: ''
});

function togglePasswordVisibility() {
  showPassword.value = !showPassword.value;
}

function validateForm(): boolean {
  errors.username = '';
  errors.password = '';
  
  if (!form.username.trim()) {
    errors.username = 'Le nom d\'utilisateur est requis';
  }
  
  if (!form.password) {
    errors.password = 'Le mot de passe est requis';
  } else if (form.password.length < 3) {
    errors.password = 'Le mot de passe doit contenir au moins 3 caractères';
  }
  
  return !errors.username && !errors.password;
}

async function handleLogin() {
  if (!validateForm()) return;

  const success = await authStore.login({ username: form.username, password: form.password });

  if (success) {
    // Redirect to invoices page or saved URL using Vue router
    const savedUrl = sessionStorage.getItem('redirectAfterLogin');
    sessionStorage.removeItem('redirectAfterLogin');
    const targetUrl = savedUrl || '/invoices';
    router.push(targetUrl);
  }
}

async function handleAzureADLogin() {
  const success = await authStore.loginWithAzureAD();

  if (success) {
    // Redirect to invoices page or saved URL
    const savedUrl = sessionStorage.getItem('redirectAfterLogin');
    sessionStorage.removeItem('redirectAfterLogin');
    router.push(savedUrl || '/invoices');
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-md">
      <div class="text-center">
        <h1 class="text-3xl font-bold text-gray-900 mb-2">{{ $t('login.brand') }}</h1>
        <h2 class="text-xl font-semibold text-gray-700">{{ $t('login.title') }}</h2>
        <p class="mt-2 text-sm text-gray-600">
          {{ $t('login.subtitle') }}
        </p>
      </div>
    </div>

    <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
      <div class="bg-white py-8 px-4 shadow rounded-lg sm:px-10">
        <!-- Alerte d'erreur -->
        <div
          v-if="error"
          class="mb-4 p-3 rounded-md bg-red-50 border border-red-200 flex items-start gap-2"
        >
          <AlertCircle class="w-5 h-5 text-red-500 mt-0.5 flex-shrink-0" />
          <div class="flex-1">
            <p class="text-sm text-red-700">{{ error }}</p>
            <button
              @click="clearError"
              class="text-xs text-red-600 hover:text-red-800 mt-1 underline"
            >
              {{ $t('common.close') }}
            </button>
          </div>
        </div>

        <form @submit.prevent="handleLogin" class="space-y-6">
          <!-- Nom d'utilisateur -->
          <div>
            <label for="username" class="block text-sm font-medium text-gray-700 mb-1">
              {{ $t('login.field.username') }}
            </label>
            <div class="relative">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <User class="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="username"
                type="text"
                v-model="form.username"
                :class="[
                  'block w-full pl-10 pr-3 py-2 border rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm',
                  errors.username ? 'border-red-300 text-red-900 placeholder-red-300' : 'border-gray-300'
                ]"
                :placeholder="$t('login.placeholder.username')"
                :disabled="loading"
                autocomplete="username"
              />
            </div>
            <p v-if="errors.username" class="mt-1 text-sm text-red-600">
              {{ errors.username }}
            </p>
          </div>

          <!-- Mot de passe -->
          <div>
            <label for="password" class="block text-sm font-medium text-gray-700 mb-1">
              {{ $t('login.field.password') }}
            </label>
            <div class="relative">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Lock class="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="password"
                :type="showPassword ? 'text' : 'password'"
                v-model="form.password"
                :class="[
                  'block w-full pl-10 pr-10 py-2 border rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm',
                  errors.password ? 'border-red-300 text-red-900 placeholder-red-300' : 'border-gray-300'
                ]"
                :placeholder="$t('login.placeholder.password')"
                :disabled="loading"
                autocomplete="current-password"
              />
              <button
                type="button"
                @click="togglePasswordVisibility"
                class="absolute inset-y-0 right-0 pr-3 flex items-center"
                :disabled="loading"
              >
                <Eye v-if="!showPassword" class="h-5 w-5 text-gray-400 hover:text-gray-600" />
                <EyeOff v-else class="h-5 w-5 text-gray-400 hover:text-gray-600" />
              </button>
            </div>
            <p v-if="errors.password" class="mt-1 text-sm text-red-600">
              {{ errors.password }}
            </p>
          </div>

          <!-- Bouton de connexion -->
          <div>
            <button
              type="submit"
              :disabled="loading"
              class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span v-if="!loading">{{ $t('login.button.signIn') }}</span>
              <div v-else class="flex items-center">
                <div class="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                {{ $t('login.button.signingIn') }}
              </div>
            </button>
          </div>
        </form>

        <!-- Azure AD Login Section -->
        <div v-if="authStore.useAzureAD" class="mt-6">
          <div class="relative">
            <div class="absolute inset-0 flex items-center">
              <div class="w-full border-t border-gray-300"></div>
            </div>
            <div class="relative flex justify-center text-sm">
              <span class="px-2 bg-white text-gray-500">{{ $t('login.or') }}</span>
            </div>
          </div>

          <div class="mt-6">
            <button
              @click="handleAzureADLogin"
              :disabled="authStore.loading"
              class="w-full flex justify-center items-center py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <svg class="w-5 h-5 mr-2" viewBox="0 0 23 23" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M11 0H0V11H11V0Z" fill="#F25022"/>
                <path d="M23 0H12V11H23V0Z" fill="#7FBA00"/>
                <path d="M11 12H0V23H11V12Z" fill="#00A4EF"/>
                <path d="M23 12H12V23H23V12Z" fill="#FFB900"/>
              </svg>
              <span v-if="!authStore.loading">{{ $t('login.button.signInWithMicrosoft') }}</span>
              <div v-else class="flex items-center">
                <div class="animate-spin rounded-full h-4 w-4 border-b-2 border-gray-700 mr-2"></div>
                {{ $t('login.button.signingIn') }}
              </div>
            </button>
          </div>
        </div>

        <!-- Lien mot de passe oublié (optionnel) -->
        <div class="mt-6 text-center">
          <a href="#" class="text-sm text-blue-600 hover:text-blue-500">
            {{ $t('login.forgotPassword') }}
          </a>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <div class="mt-8 text-center text-sm text-gray-500">
      <p>{{ $t('login.footer') }}</p>
    </div>
  </div>
</template>