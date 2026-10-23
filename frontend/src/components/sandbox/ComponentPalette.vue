<script setup lang="ts">
import { computed, ref } from 'vue'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import { type ComponentInfo, componentsOfKind, kindOrder, kinds } from '@/pipeline/catalog'

/**
 * The LDIO components, to drag onto the canvas (or add with a double click).
 */
const emit = defineEmits<{ add: [component: string] }>()

const query = ref('')

const groups = computed(() => kindOrder.map((kind) => ({
  kind,
  info: kinds[kind],
  components: componentsOfKind(kind).filter((component) => matches(component, query.value)),
})).filter((group) => group.components.length > 0))

function matches(component: ComponentInfo, text: string) {
  const needle = text.trim().toLowerCase()
  return !needle || [component.id, component.label, component.description]
    .some((value) => value.toLowerCase().includes(needle))
}

function onDragStart(event: DragEvent, component: string) {
  event.dataTransfer?.setData('application/ldio-component', component)
  event.dataTransfer?.setData('text/plain', component)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
</script>

<template>
  <div class="palette" data-cy="palette">
    <div class="palette-header">
      <div class="card-title">Components</div>
      <p class="muted small">Drag onto the canvas, or double-click to add</p>
      <IconField>
        <InputIcon class="pi pi-search" />
        <InputText v-model="query" placeholder="Search components" fluid size="small" data-cy="palette-search" />
      </IconField>
    </div>
    <div class="groups">
      <section v-for="group in groups" :key="group.kind" class="group">
        <h3 class="group-title">
          <span class="kind-dot" :style="{ background: group.info.color }" />
          {{ group.info.plural }}
          <span class="muted count">{{ group.components.length }}</span>
        </h3>
        <div
          v-for="component in group.components"
          :key="component.id"
          v-tooltip.right="{ value: component.description, showDelay: 400 }"
          class="item"
          :style="{ '--kind': group.info.color }"
          draggable="true"
          tabindex="0"
          role="button"
          :aria-label="`Add ${component.label}`"
          :data-cy="`palette-${component.id}`"
          @dragstart="onDragStart($event, component.id)"
          @dblclick="emit('add', component.id)"
          @keydown.enter="emit('add', component.id)"
        >
          <span class="item-icon"><i :class="component.icon" /></span>
          <span class="item-text">
            <span class="item-label">{{ component.label }}</span>
            <span class="item-id">{{ component.id }}</span>
          </span>
          <i class="pi pi-bars grip" />
        </div>
      </section>
      <p v-if="!groups.length" class="muted small empty">No component matches “{{ query }}”</p>
    </div>
  </div>
</template>

<style scoped>
.palette {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.palette-header {
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--card-border);
}

.palette-header p {
  margin: 2px 0 12px;
}

.groups {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px 16px;
}

.group {
  margin-top: 10px;
}

.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 6px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--muted);
}

.count {
  font-weight: 500;
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 8px;
  border-radius: 9px;
  border: 1px solid transparent;
  cursor: grab;
  user-select: none;
  transition: background 0.15s, border-color 0.15s;
}

.item:hover,
.item:focus-visible {
  background: var(--hover-bg);
  border-color: var(--card-border);
  outline: none;
}

.item:active {
  cursor: grabbing;
}

.item-icon {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--kind) 13%, transparent);
  color: var(--kind);
}

.item-icon .pi {
  font-size: 13px;
}

.item-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.item-label {
  font-size: 13px;
  font-weight: 500;
}

.item-id {
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
}

.grip {
  font-size: 11px;
  color: var(--muted);
  opacity: 0;
}

.item:hover .grip {
  opacity: 1;
}

.empty {
  padding: 16px 8px;
}
</style>
