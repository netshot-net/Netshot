import api from "@/api"
import { EmptyResult, VirtualizedDataTable } from "@/components"
import { useDialogConfig } from "@/dialog"
import { DeviceBadge } from "@/components/entity"
import { HardwareSupportDevice } from "@/types"
import { useLocalization } from "@/i18n"
import { CloseButton, Dialog, Heading, Portal, Skeleton, Stack, Tag, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { createColumnHelper } from "@tanstack/react-table"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"
import { QUERIES } from "../constants"

export type HardwareDeviceListButtonProps = {
  type: "eos" | "eol"
  date: number
  domain?: number[]
  group?: number[]
}

const columnHelper = createColumnHelper<HardwareSupportDevice>()

export default function HardwareDeviceListDialog(props: HardwareDeviceListButtonProps) {
  const { type, date, domain, group } = props
  const { t } = useTranslation()
  const { formatDate } = useLocalization()
  const dialogConfig = useDialogConfig()
  const isNever = !date
  const formattedDate = isNever ? t("common.never") : formatDate(date)

  const { data, isPending } = useQuery({
    queryKey: [QUERIES.DEVICE_HARDWARE_STATUS, type, date, domain, group],
    async queryFn() {
      return (await api.report.getAllHardwareSupportDevices(type, date, { domain, group })) ?? []
    },
  })

  const columns = [
    columnHelper.accessor("name", {
      cell: (info) => (
        <DeviceBadge networkClass={info.row.original.networkClass}>
          <Link
            to={`/app/devices/${info.row.original.id}/compliance?open=hardware`}
            onClick={(evt) => {
              evt.stopPropagation()
              dialogConfig.close()
            }}
          >
            {info.getValue()}
          </Link>
        </DeviceBadge>
      ),
      header: t("device.label"),
    }),
    columnHelper.accessor("family", {
      cell: (info) => <Text>{info.getValue()}</Text>,
      header: t("common.family"),
    }),
  ]

  const title = type === "eol" ? "compliance.hardware.endOfLifeDevices" : "compliance.hardware.endOfSaleDevices"
  const description = isNever
    ? type === "eol"
      ? "compliance.hardware.endOfLifeDevicesNeverDescription"
      : "compliance.hardware.endOfSaleDevicesNeverDescription"
    : type === "eol"
      ? "compliance.hardware.endOfLifeDevicesDescription"
      : "compliance.hardware.endOfSaleDevicesDescription"
  const emptyResultTitle =
    type === "eol" ? "compliance.hardware.noDeviceWithEolStatus" : "compliance.hardware.noDeviceWithEosStatus"

  return (
    <Dialog.Root
      scrollBehavior="inside"
      size="lg"
      open={dialogConfig.props.isOpen}
      closeOnEscape={false}
      closeOnInteractOutside={false}
      onOpenChange={(e) => {
        if (!e.open) {
          dialogConfig.close()
        }
      }}
      onExitComplete={() => {
        dialogConfig.remove()
      }}
    >
      <Portal>
        <Dialog.Backdrop />
        <Dialog.Positioner>
          <Dialog.Content height="60vh">
            <Dialog.Header display="flex" flexDirection="column" alignItems="start" gap="2">
              <Stack direction="row" alignItems="center" gap="4">
                <Heading as="h3" fontSize="2xl" fontWeight="semibold">
                  {t(title)}
                </Heading>
                <Tag.Root colorPalette="grey">{formattedDate}</Tag.Root>
              </Stack>
              <Text color="fg.muted" fontSize="sm">
                {t(description)}
              </Text>
            </Dialog.Header>
            <Dialog.Body overflowY="auto" display="flex" flexDirection="column" flex="1" pb="7">
              {isPending ? (
                <Stack gap="3">
                  <Skeleton h="60px" />
                  <Skeleton h="60px" />
                  <Skeleton h="60px" />
                  <Skeleton h="60px" />
                </Stack>
              ) : (
                <>
                  {(data?.length ?? 0) > 0 ? (
                    <VirtualizedDataTable data={data ?? []} columns={columns} primaryKey="id" flex="1" minH="0" />
                  ) : (
                    <EmptyResult title={t(emptyResultTitle)} description={t(description)} />
                  )}
                </>
              )}
            </Dialog.Body>
            <Dialog.CloseTrigger asChild>
              <CloseButton size="sm" variant="outline" />
            </Dialog.CloseTrigger>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  )
}
