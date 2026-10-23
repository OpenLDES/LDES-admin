<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
  icon?: string
}>()
</script>

<template>
  <div class="page-header">
    <div class="heading">
      <div v-if="icon" class="icon"><i :class="icon" /></div>
      <div class="titles">
        <div class="title-row">
          <h1>{{ title }}</h1>
          <slot name="tags" />
        </div>
        <p v-if="subtitle || $slots.subtitle" class="subtitle">
          <slot name="subtitle">{{ subtitle }}</slot>
        </p>
      </div>
    </div>
    <div v-if="$slots.actions" class="actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.heading {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.icon {
  width: 46px;
  height: 46px;
  flex: none;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--p-primary-50);
  color: var(--p-primary-600);
  border: 1px solid var(--p-primary-100);
}

.app-dark .icon {
  background: color-mix(in srgb, var(--p-primary-500) 12%, transparent);
  border-color: color-mix(in srgb, var(--p-primary-500) 25%, transparent);
  color: var(--p-primary-300);
}

.icon .pi {
  font-size: 20px;
}

.titles {
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

h1 {
  font-size: 24px;
  line-height: 1.25;
}

.subtitle {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13.5px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
</style>
