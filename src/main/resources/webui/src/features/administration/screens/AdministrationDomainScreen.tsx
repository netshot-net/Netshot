import api from "@/api"
import { DataTable, EmptyResult, Search } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { usePagination } from "@/hooks"
import { Domain } from "@/types"
import { search } from "@/utils"
import { Button, Heading, IconButton, Skeleton, Spacer, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { createColumnHelper } from "@tanstack/react-table"
import { useCallback, useMemo } from "react"
import { LuSquarePen, LuPlus, LuTrash } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import AddDomainTrigger from "../components/AddDomainTrigger"
import EditDomainTrigger from "../components/EditDomainTrigger"
import RemoveDomainTrigger from "../components/RemoveDomainTrigger"
import { QUERIES } from "../constants"
import TableButtonStack from "../components/TableButtonStack"

const columnHelper = createColumnHelper<Domain>()

export default function AdministrationDomainScreen() {
  const { t } = useTranslation()
  const pagination = usePagination()

  const { data = [], isPending } = useQuery({
    queryKey: [QUERIES.ADMIN_DEVICE_DOMAINS, pagination.query, pagination.offset, pagination.limit],
    queryFn: async () => api.admin.getAllDomains(pagination),
    select: useCallback(
      (res: Domain[]): Domain[] => {
        return search(res, "name", "description", "ipAddress").with(pagination.query)
      },
      [pagination.query]
    ),
  })

  const isSearching = Boolean(pagination.query?.trim())

  const columns = useMemo(
    () => [
      columnHelper.accessor("name", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.name"),
        enableSorting: true,
        size: 10000,
      }),
      columnHelper.accessor("description", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.description"),
        enableSorting: true,
        size: 20000,
      }),
      columnHelper.accessor("ipAddress", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("network.serverAddress"),
        enableSorting: true,
        size: 10000,
      }),
      columnHelper.display({
        id: "actions",
        cell: (info) => {
          const domain = info.row.original

          return (
            <TableButtonStack>
              <Tooltip content={t("common.edit")}>
                <EditDomainTrigger domain={domain}>
                  <IconButton aria-label={t("domain.edit")} variant="frame">
                    <LuSquarePen />
                  </IconButton>
                </EditDomainTrigger>
              </Tooltip>
              <Tooltip content={t("common.remove")}>
                <RemoveDomainTrigger domain={domain}>
                  <IconButton aria-label={t("domain.remove")} variant="frame">
                    <LuTrash />
                  </IconButton>
                </RemoveDomainTrigger>
              </Tooltip>
            </TableButtonStack>
          )
        },
        header: "",
        enableSorting: false,
        meta: {
          align: "right",
        },
        minSize: 80,
        size: 200,
      }),
    ],
    [t]
  )

  return (
    <>
      <Stack gap="6" p="9" flex="1" overflow="auto">
        <Stack gap="1">
          <Heading as="h1" fontSize="4xl">
            {t("domain.deviceDomains")}
          </Heading>
          <Text fontSize="sm" color="grey.400">
            {t("domain.deviceDomainsDescription")}
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
          <AddDomainTrigger>
            <Button variant="primary">
              <LuPlus />
              {t("common.create")}
            </Button>
          </AddDomainTrigger>
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
                title={t("domain.none")}
                description={t("device.createDomainFirst")}
              >
                <AddDomainTrigger>
                  <Button variant="outline">
                    <LuPlus />
                    {t("common.create")}
                  </Button>
                </AddDomainTrigger>
              </EmptyResult>
            )}
          </>
        )}
      </Stack>
    </>
  )
}
