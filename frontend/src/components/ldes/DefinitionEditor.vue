<script setup lang="ts">
import Button from 'primevue/button'
import Message from 'primevue/message'
import CodeEditor from '@/components/common/CodeEditor.vue'
import type { DefinitionProblem } from '@/ldes/definition'

/**
 * The Turtle of a definition next to its form: editable, in step with the form, with what the form
 * cannot show of its problems.
 */
defineProps<{
  draft: string
  parseError: string | undefined
  /** Problems of the definition as a whole */
  problems: DefinitionProblem[]
}>()
const emit = defineEmits<{ edit: [text: string]; reset: [] }>()
</script>

<template>
  <div class="definition-editor">
    <div class="row">
      <span class="label">Definition</span>
      <span class="muted small">Turtle</span>
      <span class="spacer" />
      <span v-if="!parseError" class="applied small"><i class="pi pi-check" /> In step with the form</span>
      <Button label="Start over" icon="pi pi-replay" text size="small" severity="secondary" data-cy="reset-definition"
        @click="emit('reset')" />
    </div>
    <CodeEditor :model-value="draft" language="turtle" min-height="320px" max-height="560px"
      data-cy="definition" @update:model-value="emit('edit', $event)" />
    <Message v-if="parseError" severity="error" size="small" :closable="false" data-cy="parse-error">
      Not valid Turtle: {{ parseError }}
    </Message>
    <small v-for="problem in problems" :key="problem.message" class="problem" :class="problem.severity"
      data-cy="definition-problem">
      <i :class="problem.severity === 'error' ? 'pi pi-times-circle' : 'pi pi-exclamation-triangle'" />
      {{ problem.message }}
    </small>
  </div>
</template>

<style scoped>
.definition-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.applied {
  color: var(--p-green-600);
}

.applied .pi {
  font-size: 10px;
}

.problem {
  display: flex;
  align-items: baseline;
  gap: 6px;
  line-height: 1.4;
}

.problem .pi {
  font-size: 11px;
}

.problem.error {
  color: var(--p-red-600);
}

.problem.warning {
  color: var(--p-amber-700);
}

.app-dark .problem.error {
  color: var(--p-red-300);
}

.app-dark .problem.warning {
  color: var(--p-amber-300);
}
</style>
