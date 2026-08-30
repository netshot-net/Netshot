import api from "@/api"
import { DataTable, EmptyResult, Search } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { usePagination } from "@/hooks"
import { CredentialSet } from "@/types"
import { search } from "@/utils"
import { Badge, Button, Heading, IconButton, Skeleton, Spacer, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { createColumnHelper } from "@tanstack/react-table"
import { useCallback, useMemo } from "react"
import { LuSquarePen, LuPlus, LuTrash, LuAsterisk } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import AddDeviceCredentialSetTrigger from "../components/AddDeviceCredentialSetTrigger"
import EditDeviceCredentialSetTrigger from "../components/EditDeviceCredentialSetTrigger"
import RemoveDeviceCredentialSetTrigger from "../components/RemoveDeviceCredentialSetTrigger"
import { QUERIES } from "../constants"
import TableButtonStack from "../components/TableButtonStack"

const columnHelper = createColumnHelper<CredentialSet>()

export default function AdministrationDeviceCredentialSetScreen() {
  const { t } = useTranslation()
  const pagination = usePagination()

  const { data = [], isPending } = useQuery({
    queryKey: [
      QUERIES.ADMIN_DEVICE_CREDENTIALS,
      pagination.query,
      pagination.offset,
      pagination.limit,
    ],
    queryFn: async () => api.admin.getAllCredentialSets(pagination),
    select: useCallback(
      (res: CredentialSet[]): CredentialSet[] => {
        return search(res, "name").with(pagination.query)
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
        size: 20000,
      }),
      columnHelper.accessor("type", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.type"),
        enableSorting: true,
        size: 10000,
      }),
      columnHelper.accessor("mgmtDomain", {
        cell: (info) => {
          const value = info.getValue()
          if (!value) {
            return (
              <Badge size="lg" variant="outline" marginTop="-3px" marginBottom="-3px">
                <LuAsterisk />
                {t("common.any")}
              </Badge>
            )
          }
          return <Text>{value.name}</Text>
        },
        header: t("domain.label"),
        enableSorting: true,
        size: 10000,
      }),
      columnHelper.display({
        id: "actions",
        cell: (info) => {
          const credentialSet = info.row.original

          return (
            <TableButtonStack>
              <Tooltip content={t("common.edit")}>
                <EditDeviceCredentialSetTrigger credentialSet={credentialSet}>
                  <IconButton aria-label={t("domain.edit")} variant="frame">
                    <LuSquarePen />
                  </IconButton>
                </EditDeviceCredentialSetTrigger>
              </Tooltip>
              <Tooltip content={t("common.remove")}>
                <RemoveDeviceCredentialSetTrigger credentialSet={credentialSet}>
                  <IconButton aria-label={t("domain.remove")} variant="frame">
                    <LuTrash />
                  </IconButton>
                </RemoveDeviceCredentialSetTrigger>
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
            {t("credential.deviceList")}
          </Heading>
          <Text fontSize="sm" color="grey.400">
            {t("credential.deviceListDescription")}
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
          <AddDeviceCredentialSetTrigger>
            <Button variant="primary">
              <LuPlus />
              {t("common.create")}
            </Button>
          </AddDeviceCredentialSetTrigger>
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
                title={t("credential.noneForDevice")}
                description={t("credential.canCreate")}
              >
                <AddDeviceCredentialSetTrigger>
                  <Button variant="outline">
                    <LuPlus />
                    {t("common.create")}
                  </Button>
                </AddDeviceCredentialSetTrigger>
              </EmptyResult>
            )}
          </>
        )}
      </Stack>
    </>
  )
}
