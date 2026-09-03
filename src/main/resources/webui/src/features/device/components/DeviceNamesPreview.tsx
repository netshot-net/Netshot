import { useCustomDialog } from "@/dialog"
import { Device, LightDevice } from "@/types"
import { Button, Stack, Text } from "@chakra-ui/react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import DeviceReorderDialog from "./DeviceReorderDialog"

export type DeviceNamesPreviewProps = {
  devices: (LightDevice | Device)[]
  onReorder?(devices: (LightDevice | Device)[]): void
}

const PREVIEW_LIMIT = 5

export default function DeviceNamesPreview(props: DeviceNamesPreviewProps) {
  const { devices, onReorder } = props
  const { t } = useTranslation()
  const dialog = useCustomDialog()

  const [orderedDevices, setOrderedDevices] = useState(devices)

  const preview = orderedDevices
    .slice(0, PREVIEW_LIMIT)
    .map((device) => device.name)
    .join(", ")
  const hasMore = orderedDevices.length > PREVIEW_LIMIT

  function openReorderDialog() {
    dialog.open(
      <DeviceReorderDialog
        devices={orderedDevices}
        onSave={(next) => {
          setOrderedDevices(next)
          onReorder?.(next)
        }}
      />,
      { size: "lg" }
    )
  }

  return (
    <Stack direction="row" alignItems="center" gap="3" flexWrap="wrap">
      <Text>
        {preview}
        {hasMore ? "…" : ""}
      </Text>
      <Button size="xs" variant="ghost" onClick={openReorderDialog}>
        {t("device.viewAllDevices", { count: orderedDevices.length })}
      </Button>
    </Stack>
  )
}
