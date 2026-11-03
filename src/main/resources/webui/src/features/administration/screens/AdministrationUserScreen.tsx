import { User } from "@/api/admin"
import { useListAll } from "@/hooks/useListAll"
import { DataTable, EmptyResult, Search } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { useAuth } from "@/contexts"
import { Level } from "@/types"
import { useSearchQuery, useUserLevelOptions } from "@/hooks"
import {
  Badge,
    Button,
  Heading,
  IconButton,
  Skeleton,
  Spacer,
  Stack,
  Text,
} from "@chakra-ui/react"
import { createColumnHelper } from "@tanstack/react-table"
import { useMemo } from "react"
import { LuCloud, LuDatabase, LuSquarePen, LuPlus, LuTrash } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import AddUserTrigger from "../components/AddUserTrigger"
import EditUserTrigger from "../components/EditUserTrigger"
import RemoveUserTrigger from "../components/RemoveUserTrigger"
import { QUERIES } from "../constants"
import TableButtonStack from "../components/TableButtonStack"

const columnHelper = createColumnHelper<User>()

export default function AdministrationUserScreen() {
  const { t } = useTranslation()
  const searchBox = useSearchQuery()
  const { user: currentUser } = useAuth()
  const userLevelOptions = useUserLevelOptions()

  const { rows: data, isPending } = useListAll<User>(
    [QUERIES.ADMIN_USERS],
    "/users",
    { q: searchBox.query, sort: "username" }
  )

  const isSearching = Boolean(searchBox.query?.trim())

  const columns = useMemo(
    () => [
      columnHelper.accessor("username", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("user.username"),
        enableSorting: true,
      }),
      columnHelper.accessor("local", {
        cell: (info) => {
          const value = info.getValue()

          return (
            <Badge size="lg" variant="outline" marginTop="-3px" marginBottom="-3px">
              {value ? <LuDatabase /> : <LuCloud />}
              {t(value ? "user.localType" : "user.remoteType")}
            </Badge>
          )
        },
        header: t("common.type"),
        enableSorting: true,
        minSize: 90,
      }),
      columnHelper.accessor("level", {
        cell: (info) => {
          const value = info.getValue()
          const label = userLevelOptions.getLabelByValue(value as Level)

          return <Text>{label ? label : t("common.unknownLabel")}</Text>
        },
        header: t("user.permissionLevel"),
        enableSorting: true,
      }),
      columnHelper.display({
        id: "actions",
        cell: (info) => {
          const user = info.row.original
          const isCurrentUser = user.username === currentUser?.username

          if (isCurrentUser) {
            return <TableButtonStack />
          }

          return (
            <TableButtonStack>
              <Tooltip content={t("common.edit")}>
                <EditUserTrigger user={user}>
                  <IconButton aria-label={t("user.edit")} variant="frame">
                    <LuSquarePen />
                  </IconButton>
                </EditUserTrigger>
              </Tooltip>
              <Tooltip content={t("common.remove")}>
                <RemoveUserTrigger user={user}>
                  <IconButton aria-label={t("user.remove")} variant="frame">
                    <LuTrash />
                  </IconButton>
                </RemoveUserTrigger>
              </Tooltip>
            </TableButtonStack>
          )
        },
        header: "",
        enableSorting: false,
        meta: {
          align: "right",
        },
      }),
    ],
    [t, userLevelOptions, currentUser?.username]
  )

  return (
    <>
      <Stack gap="6" p="9" flex="1" overflow="auto">
        <Stack gap="1">
          <Heading as="h1" fontSize="4xl">
            {t("user.list")}
          </Heading>
          <Text fontSize="sm" color="grey.400">
            {t("user.listDescription")}
          </Text>
        </Stack>
        <Stack direction="row" gap="3">
          <Search
            placeholder={t("common.searchPlaceholder")}
            onQuery={searchBox.onQuery}
            onClear={searchBox.onQueryClear}
            w="30%"
          />
          <Spacer />
          <AddUserTrigger>
            <Button variant="primary">
              <LuPlus />
              {t("common.create")}
            </Button>
          </AddUserTrigger>
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
                title={t("user.none")}
                description={t("user.canCreate")}
              >
                <AddUserTrigger>
                  <Button variant="outline">
                    <LuPlus />
                    {t("common.create")}
                  </Button>
                </AddUserTrigger>
              </EmptyResult>
            )}
          </>
        )}
      </Stack>
    </>
  )
}
