<script setup lang="ts">
import { computed, ref } from 'vue'
import Chart from 'primevue/chart'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import SelectButton from 'primevue/selectbutton'
import type { Chart as ChartJs, Plugin, TooltipItem } from 'chart.js'
import EmptyState from '@/components/common/EmptyState.vue'
import type { ThroughputSeries } from '@/domain/dashboard'
import { useUiStore } from '@/stores/ui'
import { formatNumber, formatTime } from '@/utils/format'

/**
 * Members ingested per hour, one line per LDES server. Categorical slots 1-3 of the validated
 * palette (light and dark steps), a legend, direct end labels where they don't collide, a
 * crosshair tooltip listing every series, and a table view with the same numbers.
 */
const props = defineProps<{ series: ThroughputSeries[] }>()

const ui = useUiStore()
const mode = ref<'chart' | 'table'>('chart')
const modes = [
  { icon: 'pi pi-chart-line', value: 'chart', label: 'Chart' },
  { icon: 'pi pi-table', value: 'table', label: 'Table' },
]

const palette = computed(() => ui.dark
  ? { series: ['#3987e5', '#d95926', '#199e70'], grid: '#27272a', ink: '#9ca3af', text: '#e4e4e7', surface: '#18181b' }
  : { series: ['#2a78d6', '#eb6834', '#1baf7a'], grid: '#e8ebef', ink: '#6b7280', text: '#334155', surface: '#ffffff' })

const visible = computed(() => props.series.filter((series) => series.points.length).slice(0, 3))

/** Every hour any series has a point for: series start when sampling of their server started */
const hours = computed(() => [...new Set(visible.value.flatMap((series) =>
  series.points.map((point) => point.time)))].sort())

/** The members of a series per hour of the shared axis; null where it has no point */
function valuesOf(series: ThroughputSeries): (number | null)[] {
  const byHour = new Map(series.points.map((point) => [point.time, point.members]))
  return hours.value.map((hour) => byHour.get(hour) ?? null)
}

const chartData = computed(() => ({
  labels: hours.value.map((hour) => formatTime(hour)),
  datasets: visible.value.map((series, i) => ({
    label: series.label,
    data: valuesOf(series),
    borderColor: palette.value.series[i],
    backgroundColor: palette.value.series[i],
    borderWidth: 2,
    borderCapStyle: 'round',
    borderJoinStyle: 'round',
    tension: 0.35,
    // A lone point would not show without a marker
    pointRadius: series.points.length === 1 ? 4 : 0,
    pointHitRadius: 12,
    pointHoverRadius: 5,
    pointHoverBorderWidth: 2,
    pointHoverBorderColor: palette.value.surface,
    fill: false,
  })),
}))

const chartOptions = computed(() => ({
  maintainAspectRatio: false,
  animation: { duration: 400 },
  interaction: { mode: 'index', intersect: false },
  // Room for the end labels: about 7 px per character of 12 px text, after a 23 px key
  layout: { padding: { right: Math.min(180, 30 + 7 * Math.max(0, ...visible.value.map((series) => series.label.length))), top: 8 } },
  plugins: {
    legend: { display: false },
    tooltip: {
      backgroundColor: palette.value.surface,
      titleColor: palette.value.ink,
      bodyColor: palette.value.text,
      borderColor: palette.value.grid,
      borderWidth: 1,
      padding: 10,
      boxPadding: 6,
      usePointStyle: true,
      bodyFont: { weight: 600 },
      callbacks: {
        title: (items: TooltipItem<'line'>[]) => `${items[0]?.label} – members ingested`,
        label: (item: TooltipItem<'line'>) => `${formatNumber(item.parsed.y ?? 0)}  ${item.dataset.label}`,
        labelPointStyle: () => ({ pointStyle: 'line', rotation: 0 }),
      },
    },
  },
  scales: {
    x: {
      grid: { display: false },
      border: { color: palette.value.grid },
      ticks: { color: palette.value.ink, maxTicksLimit: 8, maxRotation: 0 },
    },
    y: {
      beginAtZero: true,
      grid: { color: palette.value.grid, lineWidth: 1, drawTicks: false },
      border: { display: false },
      ticks: {
        color: palette.value.ink,
        padding: 10,
        maxTicksLimit: 5,
        callback: (value: number | string) => formatNumber(Number(value)),
      },
    },
  },
}))

/** A vertical hairline at the hovered hour */
const crosshair: Plugin<'line'> = {
  id: 'crosshair',
  afterDatasetsDraw(chart: ChartJs) {
    const active = chart.tooltip?.getActiveElements() ?? []
    if (!active.length) return
    const { ctx, chartArea } = chart
    const x = active[0].element.x
    ctx.save()
    ctx.strokeStyle = palette.value.ink
    ctx.globalAlpha = 0.5
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(x, chartArea.top)
    ctx.lineTo(x, chartArea.bottom)
    ctx.stroke()
    ctx.restore()
  },
}

/** Series names at the end of their lines; a label that would collide is left to the legend */
const endLabels: Plugin<'line'> = {
  id: 'endLabels',
  afterDatasetsDraw(chart: ChartJs) {
    const { ctx, chartArea } = chart
    const labels = chart.data.datasets.map((dataset, i) => {
      const points = chart.getDatasetMeta(i).data
      // Hours without a sample are skipped points
      const last = [...points].reverse().find((point) => !(point as { skip?: boolean }).skip)
      return { label: String(dataset.label), color: String(dataset.borderColor), y: last?.y ?? 0 }
    }).sort((a, b) => a.y - b.y)
    let previous = -Infinity
    ctx.save()
    ctx.font = '500 12px Inter, system-ui, sans-serif'
    ctx.textBaseline = 'middle'
    for (const { label, color, y } of labels) {
      if (y - previous < 15) continue
      previous = y
      ctx.strokeStyle = color
      ctx.lineWidth = 2
      ctx.beginPath()
      ctx.moveTo(chartArea.right + 8, y)
      ctx.lineTo(chartArea.right + 18, y)
      ctx.stroke()
      ctx.fillStyle = palette.value.text
      ctx.fillText(label, chartArea.right + 23, y)
    }
    ctx.restore()
  },
}

const rows = computed(() => hours.value.map((hour, i) => ({
  time: formatTime(hour),
  ...Object.fromEntries(visible.value.map((series) => [series.instanceId, valuesOf(series)[i]])),
})).reverse())
</script>

<template>
  <div class="card throughput">
    <div class="card-header">
      <div>
        <div class="card-title">Members ingested per hour</div>
        <div class="card-subtitle">All event streams of each LDES server, last 24 hours</div>
      </div>
      <SelectButton v-if="visible.length" v-model="mode" :options="modes" option-value="value" :allow-empty="false"
        aria-label="Chart or table" data-cy="throughput-mode">
        <template #option="{ option }">
          <i :class="option.icon" :aria-label="option.label" />
        </template>
      </SelectButton>
    </div>
    <EmptyState v-if="!visible.length" icon="pi pi-chart-line" title="No throughput sampled yet"
      description="LDES-admin samples the members every LDES server ingested once a minute; the chart fills up as it runs." />
    <div v-else class="legend">
      <span v-for="(series, i) in visible" :key="series.instanceId" class="legend-item">
        <span class="legend-key" :style="{ background: palette.series[i] }" />{{ series.label }}
      </span>
    </div>
    <div v-if="visible.length && mode === 'chart'" class="plot">
      <Chart type="line" :data="chartData" :options="chartOptions" :plugins="[crosshair, endLabels]"
        class="chart" />
    </div>
    <DataTable v-else-if="visible.length" :value="rows" scrollable scroll-height="300px" size="small" class="numeric">
      <Column field="time" header="Hour" />
      <Column v-for="series in visible" :key="series.instanceId" :field="series.instanceId"
        :header="series.label">
        <template #body="{ data }">{{ data[series.instanceId] === null ? '–' : formatNumber(data[series.instanceId]) }}</template>
      </Column>
    </DataTable>
  </div>
</template>

<style scoped>
.throughput {
  display: flex;
  flex-direction: column;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: -4px 0 8px;
  font-size: 12.5px;
  color: var(--muted);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.legend-key {
  width: 14px;
  height: 2px;
  border-radius: 2px;
}

.plot {
  height: 300px;
}

.chart {
  height: 100%;
}

:deep(.p-selectbutton .p-togglebutton) {
  padding: 4px 10px;
}
</style>
