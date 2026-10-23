<script setup lang="ts">
defineProps<{ size?: number }>()
</script>

<template>
  <svg :width="size ?? 32" :height="size ?? 32" viewBox="0 0 64 64" aria-hidden="true">
    <defs>
      <linearGradient id="logo-gradient" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#2dd4bf" />
        <stop offset="1" stop-color="#0d9488" />
      </linearGradient>
    </defs>
    <rect width="64" height="64" rx="14" fill="#13213a" />
    <path d="M14 44 L28 24 L40 38 L52 18" fill="none" stroke="url(#logo-gradient)" stroke-width="4"
      stroke-linecap="round" stroke-linejoin="round" />
    <circle cx="14" cy="44" r="5.5" fill="#13213a" stroke="#2dd4bf" stroke-width="3.5" />
    <circle cx="28" cy="24" r="5.5" fill="#13213a" stroke="#2dd4bf" stroke-width="3.5" />
    <circle cx="40" cy="38" r="5.5" fill="#13213a" stroke="#14b8a6" stroke-width="3.5" />
    <circle cx="52" cy="18" r="5.5" fill="#14b8a6" />
  </svg>
</template>
