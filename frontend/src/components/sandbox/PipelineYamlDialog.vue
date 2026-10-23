<script setup lang="ts">
import { ref, watch } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import CodeEditor from '@/components/common/CodeEditor.vue'
import { useSandboxStore } from '@/stores/sandbox'

/**
 * The whole pipeline as LDIO YAML. Applying it redraws the canvas.
 */
const visible = defineModel<boolean>('visible', { required: true })
const sandbox = useSandboxStore()
const text = ref('')
const error = ref<string>()

watch(visible, (open) => {
  if (open) {
    text.value = sandbox.yaml
    error.value = undefined
  }
})

function apply() {
  error.value = sandbox.applyYaml(text.value)
  if (!error.value) visible.value = false
}
</script>

<template>
  <Dialog v-model:visible="visible" modal header="Pipeline YAML" :style="{ width: '760px' }" data-cy="yaml-dialog">
    <p class="muted small intro">
      The definition LDIO receives (<code>POST /admin/api/v1/pipeline</code> accepts YAML and JSON).
      Edit it and apply it to redraw the canvas.
    </p>
    <CodeEditor v-model="text" language="yaml" min-height="380px" max-height="60vh" />
    <Message v-if="error" severity="error" :closable="false" class="error">{{ error }}</Message>
    <template #footer>
      <Button label="Cancel" severity="secondary" text @click="visible = false" />
      <Button label="Apply to canvas" icon="pi pi-check" data-cy="apply-yaml" @click="apply" />
    </template>
  </Dialog>
</template>

<style scoped>
.intro {
  margin: 0 0 12px;
}

.error {
  margin-top: 12px;
}
</style>
