import api from "@/api"
import { DataTable, EmptyResult, Search } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { usePagination } from "@/hooks"
import { HttpsCaTrustMode, VaultInstance } from "@/types"
import { search } from "@/utils"
import {
  Button,
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
import AddVaultInstanceTrigger from "../components/AddVaultInstanceTrigger"
import EditVaultInstanceTrigger from "../components/EditVaultInstanceTrigger"
import RemoveVaultInstanceTrigger from "../components/RemoveVaultInstanceTrigger"
import { QUERIES } from "../constants"
import { useVaultAuthMethodOptions } from "../hooks"
import TableButtonStack from "../components/TableButtonStack"

const columnHelper = createColumnHelper<VaultInstance>()

/** HTTPS with the certificate (and hostname) actually verified - anything else (plain HTTP, or TRUST_ANY) is "not secured". */
function isConnectionSecured(vaultInstance: VaultInstance): boolean {
  return (
    vaultInstance.baseUrl?.toLowerCase().startsWith("https://") === true &&
    vaultInstance.httpsCaTrustMode !== HttpsCaTrustMode.TrustAny
  )
}

export default function AdministrationVaultInstanceScreen() {
  const { t } = useTranslation()
  const pagination = usePagination()
  const vaultAuthMethodOptions = useVaultAuthMethodOptions()

  const { data = [], isPending } = useQuery({
    queryKey: [
      QUERIES.ADMIN_VAULT_INSTANCES,
      pagination.query,
      pagination.offset,
      pagination.limit,
    ],
    queryFn: async () => api.admin.getAllVaultInstances(pagination),
    select: useCallback(
      (res: VaultInstance[]): VaultInstance[] => {
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
      columnHelper.accessor("baseUrl", {
        cell: (info) => {
          const vaultInstance = info.row.original
          return (
            <Stack direction="row" gap="2" alignItems="center">
              <Text>{info.getValue()}</Text>
              {!isConnectionSecured(vaultInstance) && (
                <Tooltip content={t("vault.connectionNotSecured")}>
                  <Icon color="red.500" size="sm" flexShrink={0}>
                    <LuShieldOff />
                  </Icon>
                </Tooltip>
              )}
            </Stack>
          )
        },
        header: t("vault.baseUrl"),
        enableSorting: true,
        size: 20000,
      }),
      columnHelper.accessor("authMethod", {
        cell: (info) => <Text>{vaultAuthMethodOptions.getLabelByValue(info.getValue())}</Text>,
        header: t("vault.authMethod"),
        enableSorting: true,
        size: 10000,
      }),
      columnHelper.display({
        id: "actions",
        cell: (info) => {
          const vaultInstance = info.row.original

          return (
            <TableButtonStack>
              <Tooltip content={t("common.edit")}>
                <EditVaultInstanceTrigger vaultInstance={vaultInstance}>
                  <IconButton aria-label={t("common.edit")} variant="frame">
                    <LuSquarePen />
                  </IconButton>
                </EditVaultInstanceTrigger>
              </Tooltip>
              <Tooltip content={t("common.remove")}>
                <RemoveVaultInstanceTrigger vaultInstance={vaultInstance}>
                  <IconButton aria-label={t("common.remove")} variant="frame">
                    <LuTrash />
                  </IconButton>
                </RemoveVaultInstanceTrigger>
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
    [t, vaultAuthMethodOptions]
  )

  return (
    <>
      <Stack gap="6" p="9" flex="1" overflow="auto">
        <Stack gap="1">
          <Heading as="h1" fontSize="4xl">
            {t("vault.list")}
          </Heading>
          <Text fontSize="sm" color="grey.400">
            {t("vault.listDescription")}
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
          <AddVaultInstanceTrigger>
            <Button variant="primary">
              <LuPlus />
              {t("common.create")}
            </Button>
          </AddVaultInstanceTrigger>
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
                title={t("vault.noneConfigured")}
                description={t("vault.canCreate")}
              >
                <AddVaultInstanceTrigger>
                  <Button variant="outline">
                    <LuPlus />
                    {t("common.create")}
                  </Button>
                </AddVaultInstanceTrigger>
              </EmptyResult>
            )}
          </>
        )}
      </Stack>
    </>
  )
}
