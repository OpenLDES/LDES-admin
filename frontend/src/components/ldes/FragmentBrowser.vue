<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Accordion from 'primevue/accordion'
import AccordionContent from 'primevue/accordioncontent'
import AccordionHeader from 'primevue/accordionheader'
import AccordionPanel from 'primevue/accordionpanel'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Skeleton from 'primevue/skeleton'
import CodeEditor from '@/components/common/CodeEditor.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { api } from '@/api'
import { errorMessage } from '@/composables/useAsync'
import { type Fragment, type Relation, relationLabel, type View } from '@/domain/ldes'
import { formatDateTime, formatNumber, shortIri } from '@/utils/format'

/**
 * Walks through a view of an event stream, the way an LDES client does: from the root node,
 * along the tree:Relations, to the pages with members.
 */
const props = defineProps<{
  serverId: string
  collection: string
  views: View[]
}>()
const view = defineModel<string | undefined>('view')

const fragment = ref<Fragment>()
const trail = ref<{ url?: string; label: string }[]>([])
const loading = ref(false)
const error = ref<string>()

const relationLooks: Record<string, { icon: string; operator: string }> = {
  GreaterThanOrEqualToRelation: { icon: 'pi pi-calendar', operator: '≥' },
  LessThanRelation: { icon: 'pi pi-calendar', operator: '<' },
  EqualToRelation: { icon: 'pi pi-tag', operator: '=' },
  GeospatiallyContainsRelation: { icon: 'pi pi-map-marker', operator: 'contains' },
  Relation: { icon: 'pi pi-arrow-right', operator: 'next' },
}

const viewOptions = computed(() => props.views.map((v) => ({ label: v.name, value: v.name })))

/** A page the fragment links to, with every relation that leads there (e.g. ≥ and < a date) */
interface Link {
  node: string
  label: string
  type: string
  /** The relation types, e.g. tree:GreaterThanOrEqualToRelation + tree:LessThanRelation */
  types: string
  path?: string | null
  relations: Relation[]
  remainingItems?: number
}

const links = computed<Link[]>(() => {
  const byNode = new Map<string, Relation[]>()
  fragment.value?.relations.forEach((relation) =>
    byNode.set(relation.node, [...(byNode.get(relation.node) ?? []), relation]))
  return [...byNode.entries()].map(([node, relations]) => {
    const remaining = relations.map((r) => r.remainingItems).filter((n): n is number => n != null)
    return {
      node,
      label: relationLabel(relations[0]),
      type: relations[0].type,
      types: [...new Set(relations.map((r) => `tree:${r.type}`))].join(' + '),
      path: relations.find((r) => r.path)?.path,
      // The same condition only once: some servers repeat relations
      relations: relations.filter((r, i) => r.value && r.type !== 'Relation' && relations
        .findIndex((other) => other.type === r.type && other.value === r.value) === i),
      remainingItems: remaining.length ? Math.max(...remaining) : undefined,
    }
  })
})

async function load(url: string | undefined, label: string, push = true) {
  if (!view.value) return
  loading.value = true
  error.value = undefined
  try {
    fragment.value = await api.getFragment(props.serverId, props.collection, view.value, url)
    trail.value = push ? [...trail.value, { url, label }] : trail.value
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

function follow(link: Link) {
  load(link.node, link.label)
}

function goTo(index: number) {
  const target = trail.value[index]
  trail.value = trail.value.slice(0, index + 1)
  load(target.url, target.label, false)
}

watch(view, (name) => {
  if (!name) return
  trail.value = []
  load(undefined, name)
}, { immediate: true })

watch(() => props.views, (views) => {
  if (!view.value && views.length) view.value = views[0].name
}, { immediate: true })
</script>

<template>
  <div class="browser" data-cy="fragment-browser">
    <div class="card toolbar">
      <Select v-model="view" :options="viewOptions" option-label="label" option-value="value"
        placeholder="Pick a view" class="view-select" aria-label="View" data-cy="browse-view" />
      <nav class="trail" aria-label="Fragments visited">
        <template v-for="(step, index) in trail" :key="index">
          <i v-if="index > 0" class="pi pi-angle-right" />
          <button type="button" class="trail-step" :class="{ current: index === trail.length - 1 }"
            @click="goTo(index)">
            <i v-if="index === 0" class="pi pi-sitemap" />{{ step.label }}
          </button>
        </template>
      </nav>
      <span class="spacer" />
      <Button icon="pi pi-arrow-left" label="Back" severity="secondary" text size="small"
        :disabled="trail.length < 2" @click="goTo(trail.length - 2)" />
    </div>

    <div v-if="error" class="card">
      <EmptyState icon="pi pi-times-circle" title="Could not fetch this fragment" :description="error" />
    </div>
    <Skeleton v-else-if="!fragment" height="260px" border-radius="12px" />
    <div v-else class="fragment" :class="{ loading }">
      <div class="card fragment-header">
        <div class="fragment-url">
          <span class="label">{{ fragment.root ? 'View root' : 'Fragment' }}</span>
          <code>{{ fragment.url }}</code>
        </div>
        <div class="row-wrap">
          <span class="chip">
            <i :class="fragment.immutable ? 'pi pi-lock' : 'pi pi-lock-open'" />
            {{ fragment.immutable ? 'Immutable: it will not change' : 'Mutable: it can still grow' }}
          </span>
          <span class="chip"><i class="pi pi-share-alt" />{{ links.length }} {{ links.length === 1 ? 'link' : 'links' }}</span>
          <span class="chip"><i class="pi pi-file" />{{ fragment.members.length }} members</span>
        </div>
      </div>

      <section v-if="links.length">
        <h3 class="section-title">Relations</h3>
        <div class="relations">
          <button v-for="link in links" :key="link.node" type="button"
            class="card relation clickable" data-cy="relation" @click="follow(link)">
            <span class="relation-icon"><i :class="relationLooks[link.type]?.icon ?? 'pi pi-arrow-right'" /></span>
            <span class="relation-body">
              <span class="relation-label">{{ link.label }}</span>
              <span class="muted small">
                {{ link.types }}
                <template v-if="link.path"> on {{ shortIri(link.path) }}</template>
              </span>
              <span v-for="relation in link.relations" :key="`${relation.type} ${relation.value}`"
                class="relation-value mono">
                {{ relationLooks[relation.type]?.operator ?? relation.type }} {{ relation.value }}
              </span>
            </span>
            <span v-if="link.remainingItems" class="muted small remaining">
              ~{{ formatNumber(link.remainingItems) }} members
            </span>
            <i class="pi pi-angle-right go" />
          </button>
        </div>
      </section>

      <section v-if="fragment.members.length">
        <h3 class="section-title">Members</h3>
        <div class="card flush">
          <Accordion :multiple="true">
            <AccordionPanel v-for="member in fragment.members" :key="member.id" :value="member.id">
              <AccordionHeader>
                <span class="member-header">
                  <span class="mono truncate">{{ member.id }}</span>
                  <span class="muted small">{{ formatDateTime(member.timestamp) }}</span>
                </span>
              </AccordionHeader>
              <AccordionContent>
                <CodeEditor :model-value="member.content" language="turtle" readonly min-height="0" />
              </AccordionContent>
            </AccordionPanel>
          </Accordion>
        </div>
      </section>

      <div v-if="!links.length && !fragment.members.length" class="card">
        <EmptyState icon="pi pi-inbox" title="This fragment is empty" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.browser {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
}

.view-select {
  min-width: 200px;
}

.trail {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 13px;
}

.trail .pi-angle-right {
  font-size: 11px;
  color: var(--muted);
}

.trail-step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
  padding: 3px 6px;
  border-radius: 6px;
  color: var(--p-primary-color);
  cursor: pointer;
  font: inherit;
}

.trail-step:hover {
  background: var(--hover-bg);
}

.trail-step.current {
  color: var(--text);
  font-weight: 600;
}

.fragment {
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: opacity 0.15s;
}

.fragment.loading {
  opacity: 0.55;
}

.fragment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.fragment-url {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.fragment-url code {
  overflow-wrap: anywhere;
}

.section-title {
  font-size: 13px;
  margin-bottom: 10px;
  color: var(--muted);
}

.relations {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.relation {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  text-align: left;
  font: inherit;
  color: inherit;
}

.relation-icon {
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--p-primary-50);
  color: var(--p-primary-600);
}

.app-dark .relation-icon {
  background: color-mix(in srgb, var(--p-primary-500) 14%, transparent);
  color: var(--p-primary-300);
}

.relation-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.relation-label {
  font-weight: 600;
}

.relation-value {
  font-size: 11.5px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remaining {
  white-space: nowrap;
}

.go {
  color: var(--muted);
}

.member-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  min-width: 0;
  padding-right: 12px;
}
</style>
