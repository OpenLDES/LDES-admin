import { onBeforeUnmount, ref, shallowRef } from 'vue'
import { parseTurtle, type RdfGraph, writeTurtle } from '@/ldes/turtle'

/**
 * A definition that is edited in a form and as Turtle. A change in the form writes the Turtle
 * anew; the Turtle is parsed into the graph once typing pauses.
 */
export function useTurtleDraft() {
  const graph = shallowRef<RdfGraph>({ prefixes: {}, quads: [] })
  const draft = ref('')
  const parseError = ref<string>()
  let timer: ReturnType<typeof setTimeout> | undefined

  /** A change in the form, which the Turtle reflects right away */
  function change(next: RdfGraph) {
    clearTimeout(timer)
    graph.value = next
    draft.value = writeTurtle(next)
    parseError.value = undefined
  }

  /** A change in the Turtle */
  function edit(text: string) {
    draft.value = text
    clearTimeout(timer)
    timer = setTimeout(apply, 250)
  }

  /** Parses the Turtle right away, e.g. before sending it */
  function apply() {
    clearTimeout(timer)
    const parsed = parseTurtle(draft.value)
    if (!parsed.ok) {
      parseError.value = parsed.error
      return
    }
    parseError.value = undefined
    graph.value = parsed.graph
  }

  onBeforeUnmount(() => clearTimeout(timer))

  return { graph, draft, parseError, change, edit, apply }
}
