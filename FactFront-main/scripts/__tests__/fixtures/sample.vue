<script setup lang="ts">
import { ref } from 'vue'
const toast = ref<{ text: string } | null>(null)
function onSave() {
  if (!confirm('Delete this row?')) return
  toast.value = { text: 'Saved successfully' }
}
const dynamicLabel = 'should-not-extract'
console.log('debug only')
</script>

<template>
  <div>
    <h1>Welcome back</h1>
    <input placeholder="Search…" aria-label="Close" />
    <button title="Save changes">Save</button>
    <span>{{ dynamicLabel }}</span>
    <p>You have {{ count }} items</p>
    <!-- I am a comment with English text inside -->
  </div>
</template>

<style scoped>
.foo { content: "do not extract from styles"; }
</style>
