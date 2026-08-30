import api from "@/api"
import { DataTable, EmptyResult, Search } from "@/components"
import { usePagination } from "@/hooks"
import { DeviceType, DeviceTypeProtocol } from "@/types"
import { search } from "@/utils"
import { Button, Checkbox, Heading, Skeleton, Spacer, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { CellContext, createColumnHelper } from "@tanstack/react-table"
import { useCallback, useMemo } from "react"
import { LuRefreshCcw } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import ReloadDeviceDriversTrigger from "../components/ReloadDeviceDriversTrigger"
import { QUERIES } from "../constants"

const columnHelper = createColumnHelper<DeviceType>()

export default function AdministrationDriverScreen() {
  const { t } = useTranslation()
  const pagination = usePagination()

  const { data = [], isPending } = useQuery({
    queryKey: [QUERIES.ADMIN_DRIVERS, pagination.query, pagination.offset, pagination.limit],
    queryFn: async () => api.admin.getAllDrivers(pagination),
    select: useCallback(
      (res: DeviceType[]): DeviceType[] => {
        return search(res, "name", "description").with(pagination.query)
      },
      [pagination.query]
    ),
  })

  const isSearching = Boolean(pagination.query?.trim())

  const getProtocolCheckbox = useCallback(
    (info: CellContext<DeviceType, DeviceTypeProtocol[]>, types: DeviceTypeProtocol | DeviceTypeProtocol[]) => (
      <Checkbox.Root
        readOnly
        checked={(Array.isArray(types) ? types : [types]).some((type) => info.getValue()?.includes(type))}
        colorPalette="green"
      >
        <Checkbox.HiddenInput />
        <Checkbox.Control>
          <Checkbox.Indicator />
        </Checkbox.Control>
      </Checkbox.Root>
    ),
    []
  )

  const columns = useMemo(
    () => [
      columnHelper.accessor("name", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.name"),
        enableSorting: true,
        size: 20000,
      }),
      columnHelper.accessor("description", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.description"),
        enableSorting: true,
        size: 50000,
      }),
      columnHelper.accessor("protocols", {
        id: "protocol.ssh",
        cell: (info) => getProtocolCheckbox(info, DeviceTypeProtocol.Ssh),
        header: t("network.ssh"),
        size: 50,
        minSize: 70,
        meta: {
          align: "center",
        },
      }),
      columnHelper.accessor("protocols", {
        id: "protocol.telnet",
        cell: (info) => getProtocolCheckbox(info, DeviceTypeProtocol.Telnet),
        header: t("network.telnet"),
        size: 50,
        minSize: 70,
        meta: {
          align: "center",
        },
      }),
      columnHelper.accessor("protocols", {
        id: "protocol.snmp",
        cell: (info) => getProtocolCheckbox(info, [
          DeviceTypeProtocol.SnmpV1, DeviceTypeProtocol.SnmpV2c, DeviceTypeProtocol.SnmpV3,
        ]),
        header: t("network.snmp"),
        size: 50,
        minSize: 70,
        meta: {
          align: "center",
        },
      }),
      columnHelper.accessor("protocols", {
        id: "protocol.http",
        cell: (info) => getProtocolCheckbox(info, [DeviceTypeProtocol.Http, DeviceTypeProtocol.Https]),
        header: t("network.http"),
        size: 50,
        minSize: 70,
        meta: {
          align: "center",
        },
      }),
      columnHelper.accessor("version", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.version"),
        enableSorting: true,
        size: 10000,
      }),
      columnHelper.accessor("sourceHash", {
        cell: (info) => <Text>{info.getValue()?.substring(0, 8)}</Text>,
        header: t("common.hash"),
        enableSorting: true,
        size: 10000,
      }),
      columnHelper.accessor("author", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.author"),
        enableSorting: true,
        size: 10000,
      }),
    ],
    [t, getProtocolCheckbox]
  )

  return (
    <>
      <Stack gap="6" p="9" flex="1" overflow="auto">
        <Stack gap="1">
          <Heading as="h1" fontSize="4xl">
            {t("admin.drivers")}
          </Heading>
          <Text fontSize="sm" color="grey.400">
            {t("admin.driversDescription")}
          </Text>
        </Stack>
        <Stack direction="row" gap="3">
          <Search
            placeholder={t("common.searchPlaceholder")}
            onQuery={pagination.onQuery}
            onClear={pagination.onQueryClear}
            w="30%"
          />
          <Spacer />
          <ReloadDeviceDriversTrigger>
            <Button>
              <LuRefreshCcw />
              {t("admin.driver.reload")}
            </Button>
          </ReloadDeviceDriversTrigger>
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
                title={t("admin.driver.none")}
                description={t("admin.driver.wrongMessage")}
              />
            )}
          </>
        )}
      </Stack>
    </>
  )
}
