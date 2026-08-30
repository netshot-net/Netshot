import api from "@/api"
import { DataTable, EmptyResult, Search } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { usePagination, useUserLevelOptions } from "@/hooks"
import { ApiToken } from "@/types"
import { search } from "@/utils"
import { Button, Heading, IconButton, Skeleton, Spacer, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { createColumnHelper } from "@tanstack/react-table"
import { useCallback, useMemo } from "react"
import { LuPlus, LuTrash } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import AddApiTokenTrigger from "../components/AddApiTokenTrigger"
import RemoveApiTokenTrigger from "../components/RemoveApiTokenTrigger"
import { QUERIES } from "../constants"
import TableButtonStack from "../components/TableButtonStack"

const columnHelper = createColumnHelper<ApiToken>()

export default function AdministrationApiTokenScreen() {
  const { t } = useTranslation()
  const pagination = usePagination()
  const userLevelOptions = useUserLevelOptions()

  const { data = [], isPending } = useQuery({
    queryKey: [QUERIES.ADMIN_API_TOKENS, pagination.query, pagination.offset, pagination.limit],
    queryFn: async () => api.admin.getAllApiTokens(pagination),
    select: useCallback(
      (res: ApiToken[]): ApiToken[] => {
        return search(res, "description").with(pagination.query)
      },
      [pagination.query]
    ),
  })

  const isSearching = Boolean(pagination.query?.trim())

  const columns = useMemo(
    () => [
      columnHelper.accessor("description", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.description"),
        enableSorting: true,
        size: 20000,
      }),
      columnHelper.accessor("level", {
        cell: (info) => <Text>{userLevelOptions.getByValue(info.getValue())?.label}</Text>,
        header: t("user.permissionLevel"),
        enableSorting: true,
        size: 10000,
        minSize: 200,
      }),
      columnHelper.display({
        id: "actions",
        cell: (info) => {
          const apiToken = info.row.original

          return (
            <TableButtonStack>
              <Tooltip content={t("common.remove")}>
                <RemoveApiTokenTrigger apiToken={apiToken}>
                  <IconButton aria-label={t("api.removeToken")} variant="frame">
                    <LuTrash />
                  </IconButton>
                </RemoveApiTokenTrigger>
              </Tooltip>
            </TableButtonStack>
          )
        },
        header: "",
        meta: {
          align: "right",
        },
        size: 100,
        minSize: 50,
      }),
    ],
    [t, userLevelOptions]
  )

  return (
    <>
      <Stack gap="6" p="9" flex="1" overflow="auto">
        <Stack gap="1">
          <Heading as="h1" fontSize="4xl">
            {t("api.tokens")}
          </Heading>
          <Text fontSize="sm" color="grey.400">
            {t("api.tokensDescription")}
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
          <AddApiTokenTrigger>
            <Button variant="primary">
              <LuPlus />
              {t("common.create")}
            </Button>
          </AddApiTokenTrigger>
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
                title={t("api.none")}
                description={t("api.canCreate")}
              >
                <AddApiTokenTrigger>
                  <Button variant="outline">
                    <LuPlus />
                    {t("common.create")}
                  </Button>
                </AddApiTokenTrigger>
              </EmptyResult>
            )}
          </>
        )}
      </Stack>
    </>
  )
}
