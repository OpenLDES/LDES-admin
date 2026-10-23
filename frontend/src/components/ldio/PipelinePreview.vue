<script setup lang="ts">
import { computed, markRaw } from 'vue'
import { MarkerType, VueFlow } from '@vue-flow/core'
import PipelineNode from '@/components/sandbox/PipelineNode.vue'
import type { Pipeline } from '@/domain/ldio'
import { pipelineToGraph } from '@/pipeline/graph'

/**
 * A read-only drawing of a pipeline.
 */
const props = defineProps<{ pipeline: Pipeline; flowId: string }>()

const nodeTypes = { ldio: markRaw(PipelineNode) }
const graph = computed(() => pipelineToGraph(props.pipeline, 'LR'))
</script>

<template>
  <div class="preview">
    <VueFlow
      :id="flowId"
      :nodes="graph.nodes"
      :edges="graph.edges"
      :node-types="nodeTypes"
      :default-edge-options="{ markerEnd: { type: MarkerType.ArrowClosed, width: 18, height: 18 } }"
      :nodes-draggable="false"
      :nodes-connectable="false"
      :elements-selectable="false"
      :zoom-on-scroll="false"
      :zoom-on-double-click="false"
      :zoom-on-pinch="false"
      :pan-on-drag="false"
      :prevent-scrolling="false"
      :fit-view-on-init="true"
    />
  </div>
</template>

<style scoped>
.preview {
  height: 230px;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--page-bg);
}

.preview :deep(.vue-flow__edge-path) {
  stroke: var(--p-surface-400);
  stroke-width: 2;
}
</style>
