import { exportList } from "@/api/client"
import { Tooltip } from "@/components/ui/tooltip"
import { useToast } from "@/hooks"
import { download } from "@/utils"
import { ButtonGroup, IconButton, Menu, Portal, Spacer, Stack, Text } from "@chakra-ui/react"
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
        fields: "name,mgmtAddress,status,family,driverDescription,networkClass,softwareLevel,compliant,endOfLife,endOfSale," +
          "mgmtDomain,serialNumber,softwareVersion,location,contact,tags,comments",
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
      <Text>{t("device.device", { count: total })}</Text>
      <Spacer />
      <Menu.Root positioning={{ placement: "bottom-end" }}>
        <ButtonGroup attached size="sm" variant="outline">
          <Tooltip content={t("common.selectAll")}>
            <IconButton
              aria-label={t("common.selectAll")}
              disabled={total === 0 || isSelectedAll()}
              onClick={() => selectAll()}
            >
              <LuListChecks />
            </IconButton>
          </Tooltip>
          <Tooltip content={t("device.exportList")}>
            <Menu.Trigger asChild>
              <IconButton
                aria-label={t("device.exportList")}
                loading={isExporting}
                disabled={total === 0}
              >
                <LuDownload />
              </IconButton>
            </Menu.Trigger>
          </Tooltip>
          <Tooltip content={t("device.refreshList")}>
            <IconButton aria-label={t("device.refreshList")} onClick={refresh}>
              <LuRefreshCcw />
            </IconButton>
          </Tooltip>
        </ButtonGroup>
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
  )
}
