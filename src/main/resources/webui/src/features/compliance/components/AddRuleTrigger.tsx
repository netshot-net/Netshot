import { useCustomDialog } from "@/dialog"
import type { Policy } from "@/api/policy"
import { MouseEvent } from "react"
import React from "react"
import Slot from "@/components/Slot"
import AddRuleDialog from "./AddRuleDialog"

export type AddRuleTriggerProps = { policy: Policy; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function AddRuleTrigger({ policy, children, ...rest }: AddRuleTriggerProps) {
  const dialog = useCustomDialog()

  function open(evt: MouseEvent) {
    evt?.stopPropagation()

    dialog.open(<AddRuleDialog policy={policy} />, {
      size: "xl",
    })
  }

  // Menu.Item already triggers `onClick` internally to fire `onSelect`, so binding
  // both to the same handler would call it twice; pick the one the child understands.
  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
