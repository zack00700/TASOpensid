<script setup lang="ts">
defineProps<{ rows?: number; cols?: number }>()
const r = (min: number, max: number) => Math.floor(Math.random() * (max - min + 1)) + min
const widths = ['w-16','w-20','w-24','w-28','w-32','w-36','w-40']
const rw = () => widths[r(0, widths.length - 1)]
</script>

<template>
  <tr v-for="i in (rows ?? 8)" :key="i" class="animate-pulse border-b border-slate-100">
    <td v-for="j in (cols ?? 5)" :key="j" class="px-4 py-3">
      <div :class="['h-4 bg-slate-200 rounded', rw()]" />
    </td>
  </tr>
</template>
