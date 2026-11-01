import { createOptionHook } from "@/hooks"
import { DiagnosticResultType } from "@/types"

export const useResultTypeOptions = createOptionHook([
  {
    label: "common.text",
    value: DiagnosticResultType.Text,
  },
  {
    label: "diagnostic.longText",
    value: DiagnosticResultType.LongText,
  },
  {
    label: "common.numeric",
    value: DiagnosticResultType.Numeric,
  },
  {
    label: "common.binary",
    value: DiagnosticResultType.Binary,
  },
])
