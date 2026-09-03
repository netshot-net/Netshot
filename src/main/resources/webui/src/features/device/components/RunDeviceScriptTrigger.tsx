import Slot from "@/components/Slot"
import { useCustomDialog } from "@/dialog"
import { Device, LightDevice } from "@/types"
import React, { MouseEvent } from "react"
import RunDeviceScriptDialog from "./runScript/RunDeviceScriptDialog"

export type RunDeviceScriptTriggerProps = {
  devices: LightDevice[] | Device[]
  children: React.ReactElement<Record<string, unknown>>
} & Record<string, unknown>

export default function RunDeviceScriptTrigger({ devices, children, ...rest }: RunDeviceScriptTriggerProps) {
  const dialog = useCustomDialog()

  const open = (evt: MouseEvent) => {
    evt?.stopPropagation()
    dialog.open(<RunDeviceScriptDialog devices={devices} />, {
      size: "md",
    })
  }

  return (
    <Slot onTrigger={open} {...rest}>
      {children}
    </Slot>
  )
}
