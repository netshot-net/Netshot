import { Skeleton, Spacer, Stack, Switch, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { createColumnHelper } from "@tanstack/react-table"
import { useCallback, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router"

import { listAll } from "@/api/client"
import { search } from "@/utils"
import { DataTable, EmptyResult } from "@/components"
import Search from "@/components/Search"
import { useSearchQuery } from "@/hooks"
import { DeviceModule } from "@/types"
import { useLocalization } from "@/i18n"

import { QUERIES } from "../constants"

const columnHelper = createColumnHelper<DeviceModule>()

export default function DeviceModuleScreen() {
  const params = useParams<{ id: string }>()
  const { t } = useTranslation()
  const { formatDateTime } = useLocalization()
  const searchBox = useSearchQuery()
  const [history, setHistory] = useState(false)

  const { data = [], isPending } = useQuery({
    queryKey: [
      QUERIES.DEVICE_MODULES,
      params.id,
      history,
    ],
    // The current modules, or all of them (removed ones included) for the history
    queryFn: async () =>
      listAll<DeviceModule>(`/devices/${params.id}/modules`, {
        removed: history || undefined,
        view: "full",
      }),
    select: useCallback(
      (res: DeviceModule[]): DeviceModule[] => search(res, "serialNumber", "partNumber", "slot").with(searchBox.query),
      [searchBox.query]
    ),
  })

  const isSearching = Boolean(searchBox.query?.trim())

  const columns = useMemo(() => {
    const columns = [
      columnHelper.accessor("slot", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.slot"),
        enableSorting: true,
      }),
      columnHelper.accessor("partNumber", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("device.module.partNumber"),
        enableSorting: true,
      }),
      columnHelper.accessor("serialNumber", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("device.module.serialNumber"),
        enableSorting: true,
      }),
    ]

    if (history) {
      columns.push(
        {
          accessorKey: "firstSeenDate",
          cell: (info) => <Text>{info.getValue<Date>() ? formatDateTime(info.getValue<Date>()) : t("common.nA")}</Text>,
          header: t("device.firstSeen"),
          enableSorting: true,
        },
        {
          accessorKey: "lastSeenDate",
          cell: (info) => <Text>{info.getValue<Date>() ? formatDateTime(info.getValue<Date>()) : t("common.nA")}</Text>,
          header: t("device.lastSeen"),
          enableSorting: true,
        }
      )
    }

    return columns
  }, [t, history, formatDateTime])

  return (
    <Stack gap="6" flex="1" overflow="auto">
      <Stack direction="row" alignItems="center">
        <Search
          placeholder={t("common.searchPlaceholder")}
          onQuery={searchBox.onQuery}
          onClear={searchBox.onQueryClear}
          w="25%"
        />
        <Spacer />
        <Text>{t("common.showHistory")}</Text>
        <Switch.Root checked={history} size="md" onCheckedChange={(evt) => setHistory(evt.checked)}>
          <Switch.HiddenInput />
          <Switch.Control />
        </Switch.Root>
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
          {data?.length > 0 ? (
            <DataTable columns={columns} data={data} loading={isPending} />
          ) : isSearching ? (
            <Text>{t("common.noResults")}</Text>
          ) : (
            <EmptyResult
              title={t("device.module.none")}
              description={t("device.module.noModule")}
            />
          )}
        </>
      )}
    </Stack>
  )
}
