import api from "@/api"
import { DataTable, EmptyResult, Search } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { usePagination } from "@/hooks"
import { Hook, HttpsCaTrustMode } from "@/types"
import { search } from "@/utils"
import {
  Button,
  Checkbox,
  Heading,
  Icon,
  IconButton,
  Skeleton,
  Spacer,
  Stack,
  Text,
} from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { createColumnHelper } from "@tanstack/react-table"
import { useCallback, useMemo } from "react"
import { LuSquarePen, LuPlus, LuShieldOff, LuTrash } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import AddWebhookTrigger from "../components/AddWebhookTrigger"
import EditWebhookTrigger from "../components/EditWebhookTrigger"
import RemoveWebhookTrigger from "../components/RemoveWebhookTrigger"
import { QUERIES } from "../constants"
import TableButtonStack from "../components/TableButtonStack"

const columnHelper = createColumnHelper<Hook>()

/** HTTPS with the certificate (and hostname) actually verified - anything else (plain HTTP, or TRUST_ANY) is "not secured". */
function isConnectionSecured(webhook: Hook): boolean {
  return (
    webhook.url?.toLowerCase().startsWith("https://") === true &&
    webhook.httpsCaTrustMode !== HttpsCaTrustMode.TrustAny
  )
}

export default function AdministrationApiTokenScreen() {
  const { t } = useTranslation()
  const pagination = usePagination()

  const { data = [], isPending } = useQuery({
    queryKey: [QUERIES.ADMIN_WEBHOOKS, pagination.query, pagination.offset, pagination.limit],
    queryFn: async () => api.admin.getAllHooks(pagination),
    select: useCallback(
      (res: Hook[]): Hook[] => {
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
      }),
      columnHelper.accessor("enabled", {
        cell: (info) => {
          const enabled = info.getValue()
          return (
            <Checkbox.Root readOnly checked={enabled}>
              <Checkbox.HiddenInput />
              <Checkbox.Control>
                <Checkbox.Indicator />
              </Checkbox.Control>
            </Checkbox.Root>
          )
        },
        header: t("common.enabled"),
        enableSorting: true,
        meta: {
          align: "center",
        },
      }),
      columnHelper.accessor("url", {
        cell: (info) => {
          const webhook = info.row.original
          return (
            <Stack direction="row" gap="2" alignItems="center">
              <Text>{info.getValue()}</Text>
              {!isConnectionSecured(webhook) && (
                <Tooltip content={t("webhook.connectionNotSecured")}>
                  <Icon color="red.500" size="sm" flexShrink={0}>
                    <LuShieldOff />
                  </Icon>
                </Tooltip>
              )}
            </Stack>
          )
        },
        header: t("common.url"),
        enableSorting: true,
      }),
      columnHelper.display({
        id: "actions",
        cell: (info) => {
          const webhook = info.row.original

          return (
            <TableButtonStack>
              <Tooltip content={t("common.edit")}>
                <EditWebhookTrigger webhook={webhook}>
                  <IconButton aria-label={t("webhook.edit")} variant="frame">
                    <LuSquarePen />
                  </IconButton>
                </EditWebhookTrigger>
              </Tooltip>
              <Tooltip content={t("common.remove")}>
                <RemoveWebhookTrigger webhook={webhook}>
                  <IconButton aria-label={t("webhook.remove")} variant="frame">
                    <LuTrash />
                  </IconButton>
                </RemoveWebhookTrigger>
              </Tooltip>
            </TableButtonStack>
          )
        },
        header: "",
        meta: {
          align: "right",
        },
      }),
    ],
    [t]
  )

  return (
    <>
      <Stack gap="6" p="9" flex="1" overflow="auto">
        <Stack gap="1">
          <Heading as="h1" fontSize="4xl">
            {t("webhook.list")}
          </Heading>
          <Text fontSize="sm" color="grey.400">
            {t("webhook.listDescription")}
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
          <AddWebhookTrigger>
            <Button variant="primary">
              <LuPlus />
              {t("common.create")}
            </Button>
          </AddWebhookTrigger>
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
                title={t("webhook.none")}
                description={t("webhook.canCreate")}
              >
                <AddWebhookTrigger>
                  <Button variant="outline">
                    <LuPlus />
                    {t("common.create")}
                  </Button>
                </AddWebhookTrigger>
              </EmptyResult>
            )}
          </>
        )}
      </Stack>
    </>
  )
}
