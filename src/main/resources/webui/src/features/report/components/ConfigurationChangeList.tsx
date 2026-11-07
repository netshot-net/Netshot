import { EmptyResult, Search, VirtualizedDataTable } from "@/components"
import { useAlertDialog } from "@/dialog"
import { DeviceBadge } from "@/components/entity"
import { ConfigChange } from "@/api/report"
import { useLocalization } from "@/i18n"
import { DeviceNetworkClass } from "@/types"
import { Badge, Heading, Skeleton, Spacer, Stack, Tag, Text } from "@chakra-ui/react"
import { createColumnHelper } from "@tanstack/react-table"
import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { LuX } from "react-icons/lu"
import { Link } from "react-router"
import { useConfigChanges } from "../hooks"
import { useConfigChangeFilterStore } from "../stores/useConfigChangeFilterStore"
import ConfigurationChangeCompareView from "./ConfigurationChangeCompareView"
import ConfigurationChangeFilterMenu from "./ConfigurationChangeFilterMenu"

const columnHelper = createColumnHelper<ConfigChange>()

export default function ConfigurationChangeList() {
  const { t } = useTranslation()
  const { formatDateTime, formatDayMonth } = useLocalization()
  const [query, setQuery] = useState("")
  const dialog = useAlertDialog()

  const { data, isPending } = useConfigChanges()
  const day = useConfigChangeFilterStore((s) => s.day)
  const setDay = useConfigChangeFilterStore((s) => s.setDay)

  function openCompare(change: ConfigChange) {
    dialog.open({
      title: t("common.compareChanges"),
      description: <ConfigurationChangeCompareView configId={change.id} deviceId={change.device?.id ?? 0} />,
      hideFooter: true,
      variant: "full-floating",
    })
  }

  const rows = useMemo(() => {
    let filtered = data
    if (day != null) {
      const dayEnd = day + 86_400_000
      filtered = filtered.filter((change) => {
        const changeDate = change.changeDate?.getTime() ?? 0
        return changeDate >= day && changeDate < dayEnd
      })
    }
    const text = query.trim().toLowerCase()
    if (!text) return filtered
    return filtered.filter(
      (change) =>
        (change.device?.name ?? "").toLowerCase().includes(text) ||
        (change.author ?? "").toLowerCase().includes(text)
    )
  }, [data, day, query])

  const isSearching = Boolean(query?.trim())

  const columns = useMemo(
    () => [
      columnHelper.accessor("changeDate", {
        cell: (info) => {
          const changeDate = info.getValue()
          return <Text>{changeDate ? formatDateTime(changeDate) : t("common.nA")}</Text>
        },
        header: t("time.dateTime"),
        enableSorting: true,
        size: 15000,
      }),
      columnHelper.accessor((row) => row.device?.name ?? "", {
        id: "deviceName",
        cell: (info) => (
          <DeviceBadge networkClass={info.row.original.device?.networkClass as DeviceNetworkClass | undefined}>
            <Link
              to={`/app/devices/${info.row.original.device?.id}/configurations`}
              onClick={(e) => e.stopPropagation()}
            >
              {info.getValue()}
            </Link>
          </DeviceBadge>
        ),
        header: t("device.label"),
        enableSorting: true,
        size: 15000,
      }),
      columnHelper.accessor("author", {
        cell: (info) =>
          info.getValue() && <Tag.Root colorPalette="grey">{info.getValue()}</Tag.Root>,
        header: t("common.author"),
        enableSorting: true,
        size: 15000,
      }),
    ],
    [t, formatDateTime]
  )

  return (
    <Stack gap="5" flex="1" minH="0">
      <Heading as="h2" fontSize="2xl" fontWeight="semibold">
        {t("device.config.changeList")}
      </Heading>
      <Stack direction="row" gap="3" alignItems="center">
        <Search placeholder={t("common.searchPlaceholder")} onQuery={setQuery} onClear={() => setQuery("")} w="30%" />
        {day != null && (
          <Badge variant="surface" colorPalette="green" cursor="pointer" onClick={() => setDay(day)} gap="1">
            {formatDayMonth(day)}
            <LuX />
          </Badge>
        )}
        <Spacer />
        <ConfigurationChangeFilterMenu />
      </Stack>
      {isPending ? (
        <Stack gap="3">
          <Skeleton h="60px"></Skeleton>
          <Skeleton h="60px"></Skeleton>
          <Skeleton h="60px"></Skeleton>
          <Skeleton h="60px"></Skeleton>
        </Stack>
      ) : (
        <>
          {rows.length > 0 ? (
            <VirtualizedDataTable
              zIndex={0}
              columns={columns}
              data={rows}
              loading={isPending}
              onClickRow={openCompare}
              primaryKey="id"
              flex="1"
              minH="0"
            />
          ) : isSearching ? (
            <Text>{t("common.noResults")}</Text>
          ) : (
            <EmptyResult
              title={t("device.config.noChange")}
              description={t("device.hereYouCanViewConfigChangeList")}
            ></EmptyResult>
          )}
        </>
      )}
    </Stack>
  )
}
