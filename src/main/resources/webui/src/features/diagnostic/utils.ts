import type { DiagnosticRequest } from "@/api/diagnostic"
import { DiagnosticType } from "@/types"
import { Form } from "./types"

/** Builds the request from the form: only the properties of the type of the diagnostic are sent. */
export function toDiagnosticRequest(type: DiagnosticType, values: Form): DiagnosticRequest {
  const request: DiagnosticRequest = {
    name: values.name,
    type: type as DiagnosticRequest["type"],
    resultType: values.resultType as DiagnosticRequest["resultType"],
    enabled: values.enabled,
    // null clears the target group
    targetGroup: (values.targetGroup ?? null) as DiagnosticRequest["targetGroup"],
  }
  if (type === DiagnosticType.Simple) {
    request.deviceDriver = values.deviceDriver ?? undefined
    request.cliMode = values.cliMode ?? undefined
    request.command = values.command
    request.modifierPattern = values.modifierPattern
    request.modifierReplacement = values.modifierReplacement
  } else {
    request.script = values.script
  }
  return request
}
