<script setup lang="ts">
import { computed } from 'vue'
import type { Pipeline } from '@/domain/ldio'
import { componentInfo, type ComponentKind, kinds } from '@/pipeline/catalog'

/**
 * A pipeline as a compact row of components: input → adapter → transformers → outputs.
 */
const props = defineProps<{ pipeline: Pipeline }>()

const steps = computed(() => {
  const step = (name: string, kind: ComponentKind) => ({
    name,
    kind,
    label: componentInfo(name)?.label ?? name,
    icon: componentInfo(name)?.icon ?? kinds[kind].icon,
    color: kinds[kind].color,
  })
  const chain = [step(props.pipeline.input.name, 'input')]
  if (props.pipeline.input.adapter) chain.push(step(props.pipeline.input.adapter.name, 'adapter'))
  ;(props.pipeline.transformers ?? []).forEach((t) => chain.push(step(t.name, 'transformer')))
  return { chain, outputs: (props.pipeline.outputs ?? []).map((o) => step(o.name, 'output')) }
})
</script>

<template>
  <div class="chain">
    <template v-for="(step, index) in steps.chain" :key="index">
      <span v-tooltip.top="`${kinds[step.kind].label}: ${step.name}`" class="step"
        :style="{ '--kind': step.color }">
        <i :class="step.icon" />{{ step.label }}
      </span>
      <i class="pi pi-arrow-right arrow" />
    </template>
    <span class="outputs">
      <span v-for="(step, index) in steps.outputs" :key="index"
        v-tooltip.top="`${kinds[step.kind].label}: ${step.name}`" class="step"
        :style="{ '--kind': step.color }">
        <i :class="step.icon" />{{ step.label }}
      </span>
    </span>
  </div>
</template>

<style scoped>
.chain {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 9px 2px 7px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
  background: color-mix(in srgb, var(--kind) 9%, transparent);
  border: 1px solid color-mix(in srgb, var(--kind) 28%, transparent);
}

.step .pi {
  font-size: 11px;
  color: var(--kind);
}

.arrow {
  font-size: 10px;
  color: var(--muted);
}

.outputs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
