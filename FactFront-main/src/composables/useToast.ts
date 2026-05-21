import { ref } from 'vue'

export interface Toast {
  message: string
  type: 'success' | 'error' | 'warning'
}

export function useToast() {
  const toast = ref<Toast | null>(null)
  let timer: ReturnType<typeof setTimeout> | null = null

  function showToast(message: string, type: Toast['type'] = 'success') {
    if (timer) clearTimeout(timer)
    toast.value = { message, type }
    timer = setTimeout(() => { toast.value = null }, 3500)
  }

  function dismissToast() {
    if (timer) clearTimeout(timer)
    toast.value = null
  }

  return { toast, showToast, dismissToast }
}
