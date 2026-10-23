<script setup lang="ts">
import Button from 'primevue/button'
import EmptyState from '@/components/common/EmptyState.vue'
</script>

<template>
  <div class="page">
    <div class="card">
      <EmptyState icon="pi pi-compass" title="Page not found"
        description="This page does not exist (anymore). The instance may have been removed.">
        <RouterLink to="/"><Button label="Back to the dashboard" icon="pi pi-arrow-left" /></RouterLink>
      </EmptyState>
    </div>
  </div>
</template>
