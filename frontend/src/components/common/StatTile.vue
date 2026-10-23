<script setup lang="ts">
import { computed } from 'vue'

/**
 * A headline number: label, value, context line and an optional 24-point sparkline in the
 * de-emphasis colour with the latest point in the accent.
 */
const props = defineProps<{
  label: string
  value: string
  icon: string
  caption?: string
  tone?: 'default' | 'good' | 'warning' | 'critical'
  trend?: number[]
}>()

const WIDTH = 120
const HEIGHT = 36

const sparkline = computed(() => {
  const points = props.trend ?? []
  if (points.length < 2) return undefined
  const max = Math.max(...points, 1)
  const coords = points.map((value, i) => [
    (i / (points.length - 1)) * (WIDTH - 6) + 3,
    HEIGHT - 4 - (value / max) * (HEIGHT - 8),
  ])
  return {
    path: coords.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join(' '),
    last: coords[coords.length - 1],
  }
})
</script>

<template>
  <div class="card stat" :class="tone ?? 'default'" data-cy="stat-tile">
    <div class="top">
      <span class="stat-label">{{ label }}</span>
      <span class="stat-icon"><i :class="icon" /></span>
    </div>
    <div class="bottom">
      <div>
        <div class="stat-value">{{ value }}</div>
        <div v-if="caption || $slots.caption" class="stat-caption">
          <slot name="caption">{{ caption }}</slot>
        </div>
      </div>
      <svg v-if="sparkline" class="sparkline" :viewBox="`0 0 ${WIDTH} ${HEIGHT}`" :width="WIDTH"
        :height="HEIGHT" aria-hidden="true">
        <path :d="sparkline.path" />
        <circle :cx="sparkline.last[0]" :cy="sparkline.last[1]" r="3.5" />
      </svg>
    </div>
  </div>
</template>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px;
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--muted);
}

.stat-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--p-primary-50);
  color: var(--p-primary-600);
}

.app-dark .stat-icon {
  background: color-mix(in srgb, var(--p-primary-500) 14%, transparent);
  color: var(--p-primary-300);
}

.warning .stat-icon {
  background: var(--p-amber-50);
  color: var(--p-amber-600);
}

.critical .stat-icon {
  background: var(--p-red-50);
  color: var(--p-red-600);
}

.app-dark .warning .stat-icon {
  background: color-mix(in srgb, var(--p-amber-500) 14%, transparent);
  color: var(--p-amber-300);
}

.app-dark .critical .stat-icon {
  background: color-mix(in srgb, var(--p-red-500) 14%, transparent);
  color: var(--p-red-300);
}

.bottom {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.stat-caption {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--muted);
}

.sparkline path {
  fill: none;
  stroke: var(--spark-muted);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.sparkline circle {
  fill: var(--series-1);
  stroke: var(--card-bg);
  stroke-width: 2;
}
</style>
