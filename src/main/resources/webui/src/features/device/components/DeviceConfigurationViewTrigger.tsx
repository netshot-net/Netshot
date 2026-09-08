import { useTranslation } from "react-i18next"
import { useAlertDialog } from "@/dialog"
import { ConfigLongTextAttribute, DeviceAttributeDefinition } from "@/types"
import { Button } from "@chakra-ui/react"
import { LuDownload } from "react-icons/lu"
import React from "react"
import Slot from "@/components/Slot"
import DeviceConfigurationView from "./DeviceConfigurationView"

export type DeviceConfigurationViewTriggerProps = {
  id: number
  filename?: string
  attribute: ConfigLongTextAttribute
  definition: DeviceAttributeDefinition
  children: React.ReactElement<Record<string, unknown>>
} & Record<string, unknown>

export default function DeviceConfigurationViewTrigger({ id, filename, attribute, definition, children, ...rest }: DeviceConfigurationViewTriggerProps) {
  const { t } = useTranslation()
  const dialog = useAlertDialog()

  const open = () => {
    dialog.open({
      title: t(definition?.title),
      description: <DeviceConfigurationView id={id} attribute={attribute} />,
      size: "xl",
      footerExtra: (
        <Button asChild variant="ghost">
          <a href={`/api/configs/${id}/${attribute?.name}`} download={filename} target="_blank" rel="noreferrer">
            <LuDownload />
            {t("common.download")}
          </a>
        </Button>
      ),
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
