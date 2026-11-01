import { useCustomDialog } from "@/dialog"
import type { Rule } from "@/api/rule"
import { MouseEvent } from "react"
import React from "react"
import Slot from "@/components/Slot"
import EditRuleExemptedDeviceDialog from "./EditRuleExemptedDeviceDialog"

export type EditRuleExemptedDeviceTriggerProps = { policyId: number; rule: Rule; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditRuleExemptedDeviceTrigger({ policyId, rule, children, ...rest }: EditRuleExemptedDeviceTriggerProps) {
  const dialog = useCustomDialog()

  function open(evt?: MouseEvent<HTMLButtonElement>) {
    evt?.stopPropagation()

    dialog.open(<EditRuleExemptedDeviceDialog policyId={policyId} rule={rule} />)
  }

  // Menu.Item already triggers `onClick` internally to fire `onSelect`, so binding
  // both to the same handler would call it twice; pick the one the child understands.
  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
