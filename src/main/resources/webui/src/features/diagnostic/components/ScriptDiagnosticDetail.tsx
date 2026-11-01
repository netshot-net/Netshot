import { MonacoEditor } from "@/components"
import { DiagnosticType } from "@/types"
import { useMemo } from "react"
import { useDiagnostic } from "../contexts"

export default function ScriptDiagnosticDetail() {
  const { diagnostic } = useDiagnostic()

  const language = useMemo(() => {
    if (diagnostic?.type === DiagnosticType.Python) return "python"
    return "javascript"
  }, [diagnostic?.type])

  return (
    <MonacoEditor value={diagnostic?.script ?? undefined} language={language} scriptKind="diagnostic" readOnly />
  )
}
