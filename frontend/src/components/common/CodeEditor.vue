<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { basicSetup } from 'codemirror'
import { Compartment, EditorState, type Extension } from '@codemirror/state'
import { EditorView, placeholder as placeholderText } from '@codemirror/view'
import { StreamLanguage } from '@codemirror/language'
import { json } from '@codemirror/lang-json'
import { yaml } from '@codemirror/lang-yaml'
import { sparql } from '@codemirror/legacy-modes/mode/sparql'
import { turtle } from '@codemirror/legacy-modes/mode/turtle'
import { oneDark } from '@codemirror/theme-one-dark'
import { useUiStore } from '@/stores/ui'

type CodeLanguage = 'yaml' | 'json' | 'turtle' | 'sparql' | 'text'

const props = withDefaults(defineProps<{
  modelValue: string
  language?: CodeLanguage
  readonly?: boolean
  minHeight?: string
  maxHeight?: string
  placeholder?: string
}>(), {
  language: 'text',
  readonly: false,
  minHeight: '120px',
  maxHeight: 'none',
  placeholder: undefined,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const ui = useUiStore()
const host = ref<HTMLDivElement>()
let view: EditorView | undefined

const languageSlot = new Compartment()
const themeSlot = new Compartment()
const editableSlot = new Compartment()

const base = EditorView.theme({
  '&': {
    fontSize: '12.5px',
    backgroundColor: 'var(--code-bg)',
    borderRadius: '8px',
  },
  '.cm-content': { fontFamily: 'var(--font-mono)', padding: '10px 0' },
  '.cm-gutters': {
    backgroundColor: 'var(--code-bg)',
    borderRight: '1px solid var(--card-border)',
    color: 'var(--muted)',
    borderRadius: '8px 0 0 8px',
  },
  '.cm-activeLineGutter, .cm-activeLine': { backgroundColor: 'transparent' },
  '&.cm-focused .cm-activeLine': { backgroundColor: 'color-mix(in srgb, var(--p-primary-500) 6%, transparent)' },
  '&.cm-focused': { outline: 'none' },
  '.cm-scroller': { fontFamily: 'var(--font-mono)', lineHeight: '1.55' },
})

function languageExtension(language: CodeLanguage): Extension {
  switch (language) {
    case 'yaml':
      return yaml()
    case 'json':
      return json()
    case 'turtle':
      return StreamLanguage.define(turtle)
    case 'sparql':
      return StreamLanguage.define(sparql)
    default:
      return []
  }
}

function themeExtension(dark: boolean): Extension {
  return dark ? [oneDark, EditorView.theme({ '&': { backgroundColor: 'var(--code-bg)' } }, { dark: true })] : []
}

function editableExtension(readonly: boolean): Extension {
  return [EditorState.readOnly.of(readonly), EditorView.editable.of(!readonly)]
}

onMounted(() => {
  view = new EditorView({
    parent: host.value!,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        basicSetup,
        base,
        EditorView.lineWrapping,
        languageSlot.of(languageExtension(props.language)),
        themeSlot.of(themeExtension(ui.dark)),
        editableSlot.of(editableExtension(props.readonly)),
        props.placeholder ? placeholderText(props.placeholder) : [],
        EditorView.updateListener.of((update) => {
          if (update.docChanged) emit('update:modelValue', update.state.doc.toString())
        }),
      ],
    }),
  })
})

watch(() => props.modelValue, (value) => {
  if (view && value !== view.state.doc.toString()) {
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } })
  }
})
watch(() => props.language, (language) =>
  view?.dispatch({ effects: languageSlot.reconfigure(languageExtension(language)) }))
watch(() => ui.dark, (dark) => view?.dispatch({ effects: themeSlot.reconfigure(themeExtension(dark)) }))
watch(() => props.readonly, (readonly) =>
  view?.dispatch({ effects: editableSlot.reconfigure(editableExtension(readonly)) }))

onBeforeUnmount(() => view?.destroy())
</script>

<template>
  <div ref="host" class="code-editor" :class="{ readonly }"
    :style="{ '--min-height': minHeight, '--max-height': maxHeight }" data-cy="code-editor" />
</template>

<style scoped>
.code-editor {
  border: 1px solid var(--card-border);
  border-radius: 8px;
  overflow: hidden;
}

.code-editor:focus-within:not(.readonly) {
  border-color: var(--p-primary-400);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--p-primary-500) 15%, transparent);
}

.code-editor :deep(.cm-editor) {
  min-height: var(--min-height);
  max-height: var(--max-height);
}

.code-editor :deep(.cm-scroller) {
  overflow: auto;
}
</style>
