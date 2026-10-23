<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'
import CodeEditor from '@/components/common/CodeEditor.vue'
import { api } from '@/api'
import { errorMessage } from '@/composables/useAsync'
import type { EventStream, View } from '@/domain/ldes'
import { compact } from '@/domain/rdf'

const props = defineProps<{ serverId: string; serverUrl: string; stream: EventStream }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ created: [view: View] }>()

type Template = 'pagination' | 'time' | 'geo' | 'reference' | 'retention'

const templates: { id: Template; title: string; description: string; icon: string; name: string }[] = [
  { id: 'pagination', title: 'Pagination', description: 'Pages of a fixed size, in ingest order', icon: 'pi pi-book', name: 'by-page' },
  { id: 'time', title: 'Time-based', description: 'Buckets per year, month and day', icon: 'pi pi-calendar', name: 'by-time' },
  { id: 'geo', title: 'Geospatial', description: 'Map tiles at zoom level 15', icon: 'pi pi-map', name: 'by-location' },
  { id: 'reference', title: 'Reference', description: 'A bucket per value of a property', icon: 'pi pi-tag', name: 'by-reference' },
  { id: 'retention', title: 'Recent members', description: 'Only members of the last 30 days', icon: 'pi pi-clock', name: 'recent' },
]

const template = ref<Template>('pagination')
const name = ref('by-page')
const pageSize = ref(250)
const definition = ref('')
const edited = ref(false)
const busy = ref(false)
const error = ref<string>()

const fragmentation = computed(() => {
  const timestamp = props.stream.timestampPath ? compact(props.stream.timestampPath) : 'ldes:timestampPath'
  switch (template.value) {
    case 'time':
      return `[
        a tree:HierarchicalTimeBasedFragmentation ;
        tree:maxGranularity "day" ;
        tree:fragmentationPath ${timestamp}
    ]`
    case 'geo':
      return `[
        a tree:GeospatialFragmentation ;
        tree:maxZoom 15 ;
        tree:fragmentationPath geo:asWKT
    ]`
    case 'reference':
      return `[
        a tree:ReferenceFragmentation ;
        tree:fragmentationPath dcterms:isVersionOf ;
        tree:fragmentationKey "version"
    ]`
    default:
      return ''
  }
})

const generated = computed(() => `@prefix ldes:    <https://w3id.org/ldes#> .
@prefix tree:    <https://w3id.org/tree#> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix prov:    <http://www.w3.org/ns/prov#> .
@prefix sosa:    <http://www.w3.org/ns/sosa/> .
@prefix geo:     <http://www.opengis.net/ont/geosparql#> .
@prefix server:  <${props.serverUrl}/${props.stream.collection}/> .

server:${name.value} tree:viewDescription [
    tree:fragmentationStrategy (${fragmentation.value ? ` ${fragmentation.value} ` : ''}) ;${template.value === 'retention' ? `
    ldes:retentionPolicy [
        a ldes:DurationAgoPolicy ;
        tree:value "P30D"^^xsd:duration
    ] ;` : ''}
    tree:pageSize "${pageSize.value}"^^xsd:int
] .
`)

watch(generated, (value) => {
  if (!edited.value) definition.value = value
}, { immediate: true })

watch(template, (value) => {
  name.value = templates.find((t) => t.id === value)!.name
})

watch(visible, (open) => {
  if (open) {
    template.value = 'pagination'
    name.value = 'by-page'
    pageSize.value = 250
    edited.value = false
    definition.value = generated.value
    error.value = undefined
  }
})

function onEdit(value: string) {
  definition.value = value
  edited.value = value !== generated.value
}

async function submit() {
  busy.value = true
  error.value = undefined
  try {
    const view = await api.createView(props.serverId, props.stream.collection, definition.value)
    visible.value = false
    emit('created', view)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal :header="`New view on ${stream.collection}`" :style="{ width: '820px' }"
    data-cy="create-view-dialog">
    <div class="form">
      <div class="templates">
        <button v-for="option in templates" :key="option.id" type="button" class="template"
          :class="{ selected: template === option.id }" :disabled="edited" @click="template = option.id">
          <i :class="option.icon" />
          <span class="template-title">{{ option.title }}</span>
          <span class="muted small">{{ option.description }}</span>
        </button>
      </div>
      <div class="fields">
        <div class="field">
          <label for="view-name">View name</label>
          <InputText id="view-name" v-model="name" :disabled="edited" />
        </div>
        <div class="field">
          <label for="view-page-size">Page size</label>
          <InputNumber v-model="pageSize" input-id="view-page-size" :min="1" :max="10000" :disabled="edited" />
        </div>
      </div>
      <div class="row">
        <span class="label">Definition</span>
        <span class="spacer" />
        <Button v-if="edited" label="Reset to template" text size="small" @click="onEdit(generated); edited = false" />
      </div>
      <CodeEditor :model-value="definition" language="turtle" min-height="240px" max-height="360px"
        @update:model-value="onEdit" />
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    </div>
    <template #footer>
      <Button label="Cancel" severity="secondary" text @click="visible = false" />
      <Button label="Create view" icon="pi pi-plus" :loading="busy" data-cy="create-view-submit" @click="submit" />
    </template>
  </Dialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.templates {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}

.template {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.template .pi {
  color: var(--p-primary-color);
  margin-bottom: 4px;
}

.template.selected {
  border-color: var(--p-primary-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--p-primary-500) 15%, transparent);
}

.template:disabled {
  opacity: 0.6;
  cursor: default;
}

.template-title {
  font-weight: 600;
}

.fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-weight: 500;
}
</style>
