<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import CodeEditor from '@/components/common/CodeEditor.vue'
import { useAction } from '@/composables/useAsync'

/**
 * Edits an RDF document (a shape, DCAT metadata) and saves it through the given action.
 */
const props = defineProps<{
  document: string | undefined
  title: string
  description?: string
  save: (document: string) => Promise<unknown>
  savedMessage: string
}>()

const draft = ref(props.document ?? '')
const { run, busy } = useAction()
const dirty = computed(() => draft.value !== (props.document ?? ''))

watch(() => props.document, (document) => {
  draft.value = document ?? ''
})

async function submit() {
  await run(() => props.save(draft.value), props.savedMessage)
}
</script>

<template>
  <div class="card">
    <div class="card-header">
      <div>
        <div class="card-title">{{ title }}</div>
        <div v-if="description" class="card-subtitle">{{ description }}</div>
      </div>
      <div class="row">
        <span v-if="dirty" class="chip"><i class="pi pi-pencil" />Unsaved changes</span>
        <Button label="Revert" severity="secondary" text size="small" :disabled="!dirty"
          @click="draft = document ?? ''" />
        <Button label="Save" icon="pi pi-check" size="small" :disabled="!dirty" :loading="busy"
          data-cy="save-document" @click="submit" />
      </div>
    </div>
    <CodeEditor v-model="draft" language="turtle" min-height="320px" max-height="560px" />
  </div>
</template>
