import { createOptionHook } from "@/hooks"
import { DiagnosticResultType } from "@/types"
import { tKey } from "@/i18n/tKey"

export const useResultTypeOptions = createOptionHook([
  {
    label: tKey("common.text"),
    value: DiagnosticResultType.Text,
  },
  {
    label: tKey("diagnostic.longText"),
    value: DiagnosticResultType.LongText,
  },
  {
    label: tKey("common.numeric"),
    value: DiagnosticResultType.Numeric,
  },
  {
    label: tKey("common.binary"),
    value: DiagnosticResultType.Binary,
  },
])
