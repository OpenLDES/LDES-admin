<script setup lang="ts">
import type { InstallStep, StepStatus } from '@/examples/install'

/**
 * The progress of creating examples: per example, its steps with what became of them.
 */
defineProps<{ groups: { title: string; icon: string; steps: InstallStep[] }[] }>()

const icons: Record<StepStatus, string> = {
  pending: 'pi pi-circle',
  running: 'pi pi-spin pi-spinner',
  done: 'pi pi-check',
  skipped: 'pi pi-minus-circle',
  failed: 'pi pi-times-circle',
}
</script>

<template>
  <div class="groups" data-cy="example-steps">
    <section v-for="group in groups" :key="group.title" class="group">
      <h3 class="group-title"><i :class="group.icon" /> {{ group.title }}</h3>
      <ol class="steps">
        <li v-for="step in group.steps" :key="step.part" :class="step.status" data-cy="example-step">
          <i :class="icons[step.status]" />
          <span>{{ step.label }}</span>
          <span v-if="step.detail" class="detail">{{ step.detail }}</span>
        </li>
      </ol>
    </section>
  </div>
</template>

<style scoped>
.groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
}

.group-title .pi {
  font-size: 12px;
  color: var(--muted);
}

.steps {
  list-style: none;
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--p-content-hover-background);
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
}

.steps li {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
}

.steps .pi {
  font-size: 12px;
  width: 14px;
}

.detail {
  flex-basis: 100%;
  padding-left: 24px;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.done,
.running {
  color: var(--text);
}

.running {
  font-weight: 500;
}

.done .pi {
  color: var(--p-green-600);
}

.failed {
  color: var(--p-red-600);
}

.app-dark .failed {
  color: var(--p-red-300);
}
</style>
