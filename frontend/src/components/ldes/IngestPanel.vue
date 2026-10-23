<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import CodeEditor from '@/components/common/CodeEditor.vue'
import { api } from '@/api'
import { useAction } from '@/composables/useAsync'
import { memberTemplate } from '@/ldes/memberTemplates'
import { type EventStream, type RdfFormat, rdfFormats } from '@/domain/ldes'
import { formatTime } from '@/utils/format'

/**
 * Ingests a member directly on an event stream of an LDES server.
 */
const props = defineProps<{
  serverId: string
  streams: EventStream[]
  collection?: string
}>()
const emit = defineEmits<{ ingested: [collection: string] }>()

const selected = ref(props.collection ?? props.streams[0]?.collection)
const format = ref<RdfFormat>('text/turtle')
const member = ref(selected.value ? memberTemplate(selected.value) : '')
const history = ref<{ collection: string; time: string }[]>([])
const { run, busy } = useAction()

const options = computed(() => props.streams.map((stream) => ({
  label: stream.collection,
  value: stream.collection,
})))

watch(selected, (collection) => {
  if (collection && format.value === 'text/turtle') member.value = memberTemplate(collection)
})

async function ingest() {
  const collection = selected.value
  if (!collection) return
  const done = await run(async () => {
    await api.ingestMember(props.serverId, collection, member.value, format.value)
    return true
  }, `Member ingested in ${collection}`)
  if (done) {
    history.value = [{ collection, time: new Date().toISOString() }, ...history.value].slice(0, 6)
    member.value = memberTemplate(collection, new Date(Date.now() + history.value.length * 61_000))
    emit('ingested', collection)
  }
}
</script>

<template>
  <div class="ingest" data-cy="ingest-panel">
    <div class="card">
      <div class="card-header">
        <div>
          <div class="card-title">Ingest a member</div>
          <div class="card-subtitle">Posted to the ingest endpoint of the event stream, as is</div>
        </div>
      </div>
      <div class="controls">
        <div class="field">
          <label class="label" for="ingest-stream">Event stream</label>
          <Select v-model="selected" input-id="ingest-stream" :options="options" option-label="label"
            option-value="value" placeholder="Pick an event stream" class="select" data-cy="ingest-stream" />
        </div>
        <div class="field">
          <label class="label" for="ingest-format">Format</label>
          <Select v-model="format" input-id="ingest-format" :options="rdfFormats" option-label="label"
            option-value="value" class="select" />
        </div>
        <span class="spacer" />
        <Button label="New sample" icon="pi pi-refresh" severity="secondary" text
          :disabled="!selected" @click="member = memberTemplate(selected!, new Date())" />
        <Button label="Ingest" icon="pi pi-upload" :loading="busy" :disabled="!selected || !member.trim()"
          data-cy="ingest-submit" @click="ingest" />
      </div>
      <CodeEditor v-model="member" :language="format === 'application/ld+json' ? 'json' : 'turtle'"
        min-height="300px" max-height="480px" />
    </div>
    <div class="card history">
      <div class="card-header">
        <div class="card-title">Ingested in this session</div>
      </div>
      <ul v-if="history.length" class="history-list">
        <li v-for="entry in history" :key="entry.time">
          <i class="pi pi-check-circle" />
          <div>
            <div class="mono">{{ entry.collection }}</div>
            <div class="muted small">{{ formatTime(entry.time) }}</div>
          </div>
        </li>
      </ul>
      <p v-else class="muted small">
        Members you ingest show up here. The server fragments them in the background: watch the
        statistics of the event stream.
      </p>
    </div>
  </div>
</template>

<style scoped>
.ingest {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 20px;
  align-items: start;
}

@media (max-width: 1100px) {
  .ingest {
    grid-template-columns: minmax(0, 1fr);
  }
}

.controls {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.select {
  min-width: 220px;
}

.history-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-list li {
  display: flex;
  gap: 10px;
}

.history-list .pi {
  color: var(--p-green-600);
  margin-top: 2px;
}
</style>
