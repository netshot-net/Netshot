import api from "@/api"
import type { SoftwareRule } from "@/api/softwareRule"
import { NetshotError } from "@/api/httpClient"
import { DataTable, EmptyResult, Protected, Search } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import DeviceGroupBadge from "@/components/entity/DeviceGroupBadge"
import { MUTATIONS } from "@/constants"
import { useAuth } from "@/contexts"
import { useConfirmDialogWithMutation } from "@/dialog"
import { usePagination, useSoftwareLevels, useToast } from "@/hooks"
import { useList } from "@/hooks/useList"
import { useDeviceTypeOptions } from "@/features/device/hooks"
import { DeviceSoftwareLevel, Level } from "@/types"
import { Badge, Button, Heading, Icon, IconButton, Skeleton, Spacer, Stack, Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { createColumnHelper, Row } from "@tanstack/react-table"
import { useEffect, useState } from "react"
import { LuSquarePen, LuPlus, LuTrash, LuAsterisk, LuTrophy, LuMoveRight } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import AddSoftwareRuleTrigger from "../components/AddSoftwareRuleTrigger"
import EditSoftwareRuleTrigger from "../components/EditSoftwareRuleTrigger"
import RemoveSoftwareRuleTrigger from "../components/RemoveSoftwareRuleTrigger"
import RuleFieldValue from "../components/RuleFieldValue"
import { QUERIES } from "../constants"
import TableButtonStack from "@/features/administration/components/TableButtonStack"

const columnHelper = createColumnHelper<SoftwareRule>()

export default function SoftwareComplianceScreen() {
  const { t } = useTranslation()
  const pagination = usePagination()
  const { user } = useAuth()
  const [data, setData] = useState<SoftwareRule[]>([])
  const [reorderPending, setReorderPending] = useState(false)
  const dialog = useConfirmDialogWithMutation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const { getColor } = useSoftwareLevels()
  const { getOptionByDriver } = useDeviceTypeOptions()

  const {
    rows: rules,
    isPending,
    onBottomReached,
  } = useList<SoftwareRule>([QUERIES.SOFTWARE_RULE_LIST], "/compliance/software-rules", {
    family__ic: pagination.query?.trim(),
    sort: "priority",
  })
  const isSuccess = !isPending

  // Re-sync the local (reorderable) copy from the query once it settles, but
  // not while a reorder mutation is in flight (reorderPending guards against
  // a stale refetch clobbering the optimistic local order mid-mutation).
  useEffect(() => {
    if (isSuccess && !reorderPending) {
      // eslint-disable-next-line @eslint-react/set-state-in-effect
      setData(rules)
    }
  }, [isSuccess, rules, reorderPending])

  const isSearching = Boolean(pagination.query?.trim())
  const isDraggable = (user?.level || 0) >= Level.ReadWrite && !isSearching

  const columns = [
    columnHelper.accessor("group", {
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
        return <DeviceGroupBadge id={value.id!} name={value.name ?? value.display ?? ""} />
      },
      header: t("group.label"),
      size: 10000,
    }),
    columnHelper.accessor("driver", {
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
        return <Text>{getOptionByDriver(value)?.value.description ?? value}</Text>
      },
      header: t("device.type"),
      size: 10000,
    }),
    columnHelper.accessor("family", {
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
        return <RuleFieldValue value={value} isRegExp={info.row.original.familyRegExp} />
      },
      header: t("device.family"),
      size: 10000,
    }),
    columnHelper.accessor("partNumber", {
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
        return <RuleFieldValue value={value} isRegExp={info.row.original.partNumberRegExp} />
      },
      header: t("device.module.partNumber"),
      size: 10000,
    }),
    columnHelper.accessor("version", {
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
        return <RuleFieldValue value={value} isRegExp={info.row.original.versionRegExp} />
      },
      header: t("common.version"),
      size: 10000,
    }),
    columnHelper.display({
      id: "arrow",
      cell: () => <Icon><LuMoveRight /></Icon>,
      header: "",
      enableSorting: false,
      size: 30,
      minSize: 30,
      meta: {
        align: "center",
      },
    }),
    columnHelper.accessor("level", {
      cell: (info) => {
        const level = info.getValue()

        return (
          <Badge colorPalette={getColor(level as DeviceSoftwareLevel)}>
            <LuTrophy size={12} />
            {level}
          </Badge>
        )
      },
      header: t("common.level"),
      size: 10000,
    }),
    columnHelper.display({
      id: "actions",
      cell: (info) => {
        const rule = info.row.original

        return (
          <Protected minLevel={Level.ReadWrite}>
            <TableButtonStack>
              <Tooltip content={t("common.edit")}>
                <EditSoftwareRuleTrigger rule={rule}>
                  <IconButton variant="frame" aria-label={t("policy.rule.editThe")}>
                    <LuSquarePen />
                  </IconButton>
                </EditSoftwareRuleTrigger>
              </Tooltip>

              <Tooltip content={t("common.remove")}>
                <RemoveSoftwareRuleTrigger rule={rule}>
                  <IconButton variant="frame" aria-label={t("policy.rule.removeThe")}>
                    <LuTrash />
                  </IconButton>
                </RemoveSoftwareRuleTrigger>
              </Tooltip>
            </TableButtonStack>
          </Protected>
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
  ]

  const reorderMutation = useMutation({
    mutationKey: MUTATIONS.SOFTWARE_RULE_REORDER,
    mutationFn: ({ id, priority }: { id: number; priority: number }) => {
      return api.softwareRule.reorder(id, priority)
    },
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  function onDrop(row: Row<SoftwareRule>, reorderedData: SoftwareRule[]) {
    const id = row.original.id!
    // The priority of a rule is its position in the list, from 1
    const priority = reorderedData.findIndex((rule) => rule.id === id) + 1
    const originalData = [...data]

    setReorderPending(true)

    const dialogRef = dialog.open(MUTATIONS.SOFTWARE_RULE_REORDER, {
      title: t("common.changePriority"),
      description: t("policy.rule.confirmChangePriorities"),
      async onConfirm() {
        await reorderMutation.mutateAsync({ id, priority })

        setData(reorderedData)
        dialogRef.close()

        // Read the list again: the server gives the new positions of the rules
        await queryClient.invalidateQueries({ queryKey: [QUERIES.SOFTWARE_RULE_LIST] })

        setReorderPending(false)
      },
      onCancel() {
        setReorderPending(false)
        setData(originalData)
      },
    })
  }

  return (
    <>
      <Stack gap="6" p="9" flex="1" overflow="auto">
        <Heading as="h1" fontSize="4xl">
          {t("compliance.software.versionLabel")}
        </Heading>
        <Text color="fg.muted">{t("compliance.software.howItWorks")}</Text>
        <Stack direction="row" gap="3">
          <Search
            placeholder={t("common.searchPlaceholder")}
            onQuery={pagination.onQuery}
            onClear={pagination.onQueryClear}
            w="30%"
          />
          <Spacer />
          <Protected minLevel={Level.ReadWrite}>
            <Skeleton loading={!!isPending}>
              <AddSoftwareRuleTrigger>
                <Button variant="primary">
                  <LuPlus />
                  {t("policy.rule.add")}
                </Button>
              </AddSoftwareRuleTrigger>
            </Skeleton>
          </Protected>
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
            {data.length > 0 ? (
              <DataTable
                columns={columns}
                data={data}
                loading={isPending}
                draggable={isDraggable}
                primaryKey="id"
                onDropRow={onDrop}
                onBottomReached={onBottomReached}
              />
            ) : isSearching ? (
              <Text>{t("common.noResults")}</Text>
            ) : (
              <EmptyResult
                title={t("compliance.software.noRule")}
                description={t("compliance.software.canAddRule")}
              >
                <Protected minLevel={Level.ReadWrite}>
                  <AddSoftwareRuleTrigger>
                    <Button variant="outline">
                      <LuPlus />
                      {t("policy.rule.add")}
                    </Button>
                  </AddSoftwareRuleTrigger>
                </Protected>
              </EmptyResult>
            )}
          </>
        )}
      </Stack>
    </>
  )
}
