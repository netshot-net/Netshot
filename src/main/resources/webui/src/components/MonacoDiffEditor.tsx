import { colors } from "@/theme/tokens/colors"
import { Box } from "@chakra-ui/react"
import { editor } from "monaco-editor"
import "./monaco/monacoWorkerKeepAlive"
import { useLayoutEffect, useRef } from "react"

editor.defineTheme("netshot", {
  base: "vs",
  inherit: true,
  colors: {
    "editor.background": colors.white.value,
    "editorLineNumber.foreground": colors.grey[400].value,
    "editor.selectionHighlightBackground": colors.green[100].value,
  },
  rules: [
    { token: "variable", foreground: colors.red[500].value },
    { token: "constant", foreground: colors.red[500].value },
  ],
})

export type MonacoDiffEditorProps = {
  language?: string
  readOnly?: boolean
  original: string
  modified: string
}

export default function MonacoDiffEditor(props: MonacoDiffEditorProps) {
  const { original, modified, readOnly = false, language = "plaintext" } = props
  const containerRef = useRef<HTMLDivElement>(null)
  const editorRef = useRef<editor.IStandaloneDiffEditor | undefined>(undefined)
  const modelsRef = useRef<{ original: editor.ITextModel; modified: editor.ITextModel } | undefined>(undefined)

  // Create the diff editor widget once. Recreating it on every prop change
  // (as before) also silently leaked its previous pair of models, since
  // disposing the editor doesn't dispose models it didn't create itself.
  useLayoutEffect(() => {
    if (!containerRef?.current) return

    editorRef.current = editor.createDiffEditor(containerRef.current, {
      automaticLayout: true,
      theme: "netshot",
      fontSize: 14,
      fontFamily: "SF Mono",
    })

    return () => {
      editorRef.current?.dispose()
      editorRef.current = undefined
      modelsRef.current?.original.dispose()
      modelsRef.current?.modified.dispose()
      modelsRef.current = undefined
    }
  }, [])

  // Swap in a fresh pair of models whenever the content/language changes,
  // explicitly disposing the previous pair instead of leaking them.
  useLayoutEffect(() => {
    if (!editorRef.current) return

    const previousModels = modelsRef.current
    modelsRef.current = {
      original: editor.createModel(original, language),
      modified: editor.createModel(modified, language),
    }
    editorRef.current.setModel(modelsRef.current)
    editorRef.current.updateOptions({ readOnly })

    previousModels?.original.dispose()
    previousModels?.modified.dispose()
  }, [original, modified, language, readOnly])

  return <Box flex="1" ref={containerRef} />
}
