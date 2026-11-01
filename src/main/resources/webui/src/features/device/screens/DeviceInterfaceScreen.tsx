import { Badge, Icon, List, Skeleton, Span, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { createColumnHelper } from "@tanstack/react-table"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router"

import { listAll } from "@/api/client"
import { DataTable, EmptyResult } from "@/components"
import Search from "@/components/Search"
import { usePagination } from "@/hooks"
import { DeviceInterface } from "@/types"

import { QUERIES } from "../constants"
import { LuChevronsLeftRightEllipsis, LuPowerOff } from "react-icons/lu"

const columnHelper = createColumnHelper<DeviceInterface>()

/**
 * @todo: Add pagination (paginator)
 */
export default function DeviceInterfaceScreen() {
  const params = useParams<{ id: string }>()
  const { t } = useTranslation()
  const pagination = usePagination()

  const { data = [], isPending } = useQuery({
    queryKey: [
      QUERIES.DEVICE_INTERFACES,
      params.id,
      pagination.query,
    ],
    // The server searches the name and description
    queryFn: async () =>
      listAll<DeviceInterface>(`/devices/${params.id}/interfaces`, { q: pagination.query?.trim(), view: "full" }),
  })

  const isSearching = Boolean(pagination.query?.trim())

  const columns = useMemo(
    () =>
      [
        data.find((i) => !!i.virtualDevice) &&
          columnHelper.accessor("virtualDevice", {
            cell: (info) => <Text>{info.getValue()}</Text>,
            header: t("device.virtualDevice"),
            enableSorting: true,
            size: 15000,
          }),
        columnHelper.accessor("name", {
          cell: (info) => <Text>{info.getValue()}</Text>,
          header: t("common.name"),
          enableSorting: true,
          size: 15000,
        }),
        columnHelper.accessor("enabled", {
          cell: (info) =>
            info.getValue() === false ? (
              <Badge variant="surface" size="md" display="inline-flex" alignItems="center" gap="1" colorPalette="red">
                <Icon size="sm" flexShrink={0}>
                  <LuPowerOff />
                </Icon>
                <Span minW="0" overflow="hidden" textOverflow="ellipsis" whiteSpace="nowrap">
                  {t("common.disabled")}
                </Span>
              </Badge>
            ) : null,
          header: "",
          size: 10000,
        }),
        columnHelper.accessor("level3", {
          cell: (info) =>
            info.getValue() === false ? (
              <Badge variant="surface" size="md" display="inline-flex" alignItems="center" gap="1" colorPalette="grey">
                <Icon size="sm" flexShrink={0}>
                  <LuChevronsLeftRightEllipsis />
                </Icon>
                <Span minW="0" overflow="hidden" textOverflow="ellipsis" whiteSpace="nowrap">
                  {t("device.class.l2")}
                </Span>
              </Badge>
            ) : null,
          header: "",
          size: 10000,
        }),
        columnHelper.accessor("description", {
          cell: (info) => <Text>{info.getValue()}</Text>,
          header: t("common.description"),
          enableSorting: true,
          size: 25000,
        }),
        columnHelper.accessor("macAddress", {
          cell: (info) => {
            const mac = (info.getValue() && info.getValue() !== "0000.0000.0000") ? info.getValue() : ""
            return <Text>{mac}</Text>
          },
          header: t("device.interface.macAddress"),
          enableSorting: true,
          size: 12000,
        }),
        data.find((i) => !!i.vrf) &&
          columnHelper.accessor("vrf", {
            cell: (info) => <Text>{info.getValue()}</Text>,
            header: t("common.vrf"),
            enableSorting: true,
            size: 15000,
          }),
        columnHelper.accessor("ipAddresses", {
          cell: (info) => {
            const value = info.getValue()
            if (value && value.length > 0) {
              return (
                <List.Root as="ul" listStyleType="''" lineHeight="1.5em">
                  {value.map((i) => (
                    <List.Item key={i.ip}>
                      <Text as="span">{i.ip}</Text>
                      <Text as="span" color="gray.400">
                        /{i.prefixLength}
                      </Text>
                    </List.Item>
                  ))}
                </List.Root>
              )
            }
            return ""
          },
          header: t("device.interface.ipAddress"),
          enableSorting: true,
          size: 12000,
        }),
      ].filter((c) => !!c),
    [t, data]
  )

  return (
    <Stack gap="6" overflow="auto">
      <Stack direction="row" gap="3">
        <Search
          placeholder={t("device.searchBy")}
          onQuery={pagination.onQuery}
          onClear={pagination.onQueryClear}
          w="30%"
        />
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
              title={t("device.interface.none")}
              description={t("device.interface.noInterface")}
            />
          )}
        </>
      )}
    </Stack>
  )
}
