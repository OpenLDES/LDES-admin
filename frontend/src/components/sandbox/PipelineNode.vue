<script setup lang="ts">
import { computed, inject, type Ref, ref } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import type { GraphIssue, PipelineNodeData } from '@/pipeline/graph'
import { componentInfo, kinds, summarize } from '@/pipeline/catalog'

/**
 * A pipeline component on the canvas. Inputs only have an outgoing port, outputs only an incoming
 * one; ports sit on the top and bottom, or on the sides in a left-to-right layout.
 */
const props = defineProps<NodeProps<PipelineNodeData>>()

const issuesOf = inject<(id: string) => GraphIssue[]>('pipelineIssues', () => [])
const selectedNodeId = inject<Ref<string | undefined>>('selectedNodeId', ref())

const highlighted = computed(() => props.selected || selectedNodeId.value === props.id)
const horizontal = computed(() => props.data.direction === 'LR')
const incoming = computed(() => horizontal.value ? Position.Left : Position.Top)
const outgoing = computed(() => horizontal.value ? Position.Right : Position.Bottom)

const info = computed(() => componentInfo(props.data.component))
const kind = computed(() => kinds[props.data.kind])
const summary = computed(() => summarize(props.data.config))
const issues = computed(() => issuesOf(props.id))
const hasErrors = computed(() => issues.value.some((issue) => issue.severity === 'error'))

const statusIcon = computed(() => {
  switch (props.data.runStatus) {
    case 'active':
      return 'pi pi-spin pi-spinner'
    case 'done':
      return 'pi pi-check'
    case 'filtered':
      return 'pi pi-filter-slash'
    case 'error':
      return 'pi pi-times'
    default:
      return undefined
  }
})
</script>

<template>
  <div class="ldio-node" :class="[data.kind, data.runStatus, { selected: highlighted }]" :style="{ '--kind': kind.color }"
    :data-cy="`node-${data.component}`">
    <Handle v-if="data.kind !== 'input'" type="target" :position="incoming" class="port" />
    <div class="node-icon"><i :class="info?.icon ?? kind.icon" /></div>
    <div class="node-body">
      <div class="node-kind">{{ kind.label }}</div>
      <div class="node-label">{{ info?.label ?? data.component }}</div>
      <div v-if="summary" class="node-summary" :title="summary">{{ summary }}</div>
    </div>
    <span v-if="statusIcon" class="node-status"><i :class="statusIcon" /></span>
    <span v-else-if="issues.length" class="node-warning" :class="{ errors: hasErrors }"
      :title="issues.map((issue) => issue.message).join('\n')" :data-cy="hasErrors ? 'node-errors' : 'node-warnings'">
      <i :class="hasErrors ? 'pi pi-times' : 'pi pi-exclamation-triangle'" />
    </span>
    <Handle v-if="data.kind !== 'output'" type="source" :position="outgoing" class="port" />
  </div>
</template>

<style scoped>
.ldio-node {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 210px;
  padding: 11px 12px;
  border-radius: 12px;
  background: var(--card-bg);
  border: 1px solid var(--card-border);
  border-left: 4px solid var(--kind);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06), 0 4px 12px rgba(15, 23, 42, 0.05);
  color: var(--text);
  font-family: var(--font-sans);
  transition: box-shadow 0.2s, border-color 0.2s, opacity 0.2s, transform 0.2s;
}

.ldio-node:hover {
  box-shadow: 0 2px 4px rgba(15, 23, 42, 0.08), 0 8px 24px rgba(15, 23, 42, 0.08);
}

.ldio-node.selected {
  border-color: var(--kind);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--kind) 25%, transparent), 0 8px 24px rgba(15, 23, 42, 0.08);
}

.ldio-node.active {
  transform: scale(1.03);
  border-color: var(--kind);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--kind) 30%, transparent), 0 0 24px color-mix(in srgb, var(--kind) 45%, transparent);
}

.ldio-node.skipped {
  opacity: 0.45;
}

.node-icon {
  width: 36px;
  height: 36px;
  flex: none;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--kind) 14%, transparent);
  color: var(--kind);
}

.node-icon .pi {
  font-size: 16px;
}

.node-body {
  min-width: 0;
  flex: 1;
}

.node-kind {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--kind);
}

.node-label {
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.3;
}

.node-summary {
  margin-top: 2px;
  font-size: 11.5px;
  font-family: var(--font-mono);
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-status,
.node-warning {
  position: absolute;
  top: -9px;
  right: -9px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 10px;
  color: #fff;
  border: 2px solid var(--card-bg);
}

.node-status .pi,
.node-warning .pi {
  font-size: 10px;
}

.active .node-status {
  background: var(--kind);
}

.done .node-status {
  background: var(--p-green-500);
}

.filtered .node-status {
  background: var(--p-amber-500);
}

.error .node-status {
  background: var(--p-red-500);
}

.node-warning {
  background: var(--p-amber-500);
}

.node-warning.errors {
  background: var(--p-red-500);
}

.port {
  width: 12px;
  height: 12px;
  background: var(--card-bg);
  border: 2px solid var(--kind);
}

.port:hover {
  background: var(--kind);
}
</style>
