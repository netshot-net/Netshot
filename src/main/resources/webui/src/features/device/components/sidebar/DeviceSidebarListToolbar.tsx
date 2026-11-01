import { exportList } from "@/api/client"
import { DEVICE_SUMMARY_FIELDS } from "@/api/device"
import { Tooltip } from "@/components/ui/tooltip"
import { useToast } from "@/hooks"
import { download } from "@/utils"
import { IconButton, Menu, Portal, Spacer, Stack, Text } from "@chakra-ui/react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { LuDownload, LuFileSpreadsheet, LuFileText, LuListChecks, LuRefreshCcw } from "react-icons/lu"
import { useShallow } from "zustand/react/shallow"
import { useDeviceSidebarStore } from "../../stores"

export default function DeviceSidebarListToolbar() {
  const { t } = useTranslation()
  const toast = useToast()
  const [isExporting, setIsExporting] = useState(false)
  const { isSelectedAll, total, selectAll, refresh, listParams } = useDeviceSidebarStore(
    useShallow((state) => ({
      isSelectedAll: state.isSelectedAll,
      total: state.total,
      selectAll: state.selectAll,
      refresh: state.refresh,
      listParams: state.listParams,
    }))
  )

  /** Downloads the list with the current search, group and sort (all the matching devices). */
  async function exportDevices(format: "csv" | "xlsx") {
    if (!listParams) {
      return
    }
    setIsExporting(true)
    try {
      const { blob, fileName } = await exportList("/devices", format, {
        ...listParams,
        fields: `${DEVICE_SUMMARY_FIELDS},mgmtDomain,serialNumber,softwareVersion,location,contact,tags,comments`,
      })
      await download(blob, fileName)
    } catch (err) {
      toast.error(err as never)
    } finally {
      setIsExporting(false)
    }
  }

  return (
    <Stack direction="row" alignItems="center" px="6" py="3">
      <Stack direction="row" alignItems="center" gap="1">
        <Text>{t("device.device", { count: total })}</Text>
        <Tooltip content={t("device.refreshList")}>
          <IconButton
            aria-label={t("device.refreshList")}
            variant="ghost"
            size="xs"
            color="fg.muted"
            onClick={refresh}
          >
            <LuRefreshCcw />
          </IconButton>
        </Tooltip>
        <Menu.Root positioning={{ placement: "bottom-start" }}>
          <Tooltip content={t("device.exportList")}>
            <Menu.Trigger asChild>
              <IconButton
                aria-label={t("device.exportList")}
                variant="ghost"
                size="xs"
                color="fg.muted"
                loading={isExporting}
                disabled={total === 0}
              >
                <LuDownload />
              </IconButton>
            </Menu.Trigger>
          </Tooltip>
          <Portal>
            <Menu.Positioner>
              <Menu.Content>
                <Menu.Item value="csv" onClick={() => exportDevices("csv")}>
                  <LuFileText />
                  {t("device.exportCsv")}
                </Menu.Item>
                <Menu.Item value="xlsx" onClick={() => exportDevices("xlsx")}>
                  <LuFileSpreadsheet />
                  {t("device.exportExcel")}
                </Menu.Item>
              </Menu.Content>
            </Menu.Positioner>
          </Portal>
        </Menu.Root>
      </Stack>
      <Spacer />
      <Stack direction="row" gap="2">
        {!isSelectedAll() && total > 0 && (
          <Tooltip content={t("common.selectAll")}>
            <IconButton aria-label={t("common.selectAll")} size="sm" onClick={() => selectAll()}>
              <LuListChecks />
            </IconButton>
          </Tooltip>
        )}
      </Stack>
    </Stack>
  )
}
