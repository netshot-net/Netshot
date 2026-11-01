import React, { MouseEvent } from "react"
import Slot from "@/components/Slot"
import { useCustomDialog } from "@/dialog"
import { GroupBrief } from "@/types"
import EditGroupDialog from "./EditGroupDialog"

export type EditGroupTriggerProps = { group: GroupBrief; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditGroupTrigger({ group, children, ...rest }: EditGroupTriggerProps) {
  const dialog = useCustomDialog()

  const open = (evt: MouseEvent<HTMLButtonElement>) => {
    evt?.stopPropagation()
    dialog.open(<EditGroupDialog group={group} />)
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
