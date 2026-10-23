<script setup lang="ts">
defineProps<{
  icon: string
  title: string
  description?: string
}>()
</script>

<template>
  <div class="empty">
    <div class="empty-icon"><i :class="icon" /></div>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <div v-if="$slots.default" class="empty-actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 40px 24px;
}

.empty-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  background: var(--p-content-hover-background);
  color: var(--muted);
  margin-bottom: 14px;
}

.empty-icon .pi {
  font-size: 22px;
}

h3 {
  font-size: 15px;
}

p {
  max-width: 420px;
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}

.empty-actions {
  display: flex;
  gap: 8px;
  margin-top: 18px;
}
</style>
