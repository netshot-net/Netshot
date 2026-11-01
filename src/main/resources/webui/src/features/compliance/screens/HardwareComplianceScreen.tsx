import { DataTable, EmptyResult, Protected, Search } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import DeviceGroupBadge from "@/components/entity/DeviceGroupBadge"
import type { HardwareRule } from "@/api/hardwareRule"
import { usePagination } from "@/hooks"
import { useListAll } from "@/hooks/useListAll"
import { useDeviceTypeOptions } from "@/features/device/hooks"
import { Level } from "@/types"
import { useLocalization } from "@/i18n/useLocalization"
import { Badge, Button, Heading, Icon, IconButton, Skeleton, Spacer, Stack, Text } from "@chakra-ui/react"
import { createColumnHelper } from "@tanstack/react-table"
import { useMemo } from "react"
import { LuSquarePen, LuPlus, LuTrash, LuAsterisk, LuMoveRight } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import AddHardwareRuleTrigger from "../components/AddHardwareRuleTrigger"
import EditHardwareRuleTrigger from "../components/EditHardwareRuleTrigger"
import RemoveHardwareRuleTrigger from "../components/RemoveHardwareRuleTrigger"
import RuleFieldValue from "../components/RuleFieldValue"
import { QUERIES } from "../constants"
import TableButtonStack from "@/features/administration/components/TableButtonStack"

const columnHelper = createColumnHelper<HardwareRule>()

export default function HardwareComplianceScreen() {
  const { t } = useTranslation()
  const { formatDate } = useLocalization()
  const pagination = usePagination()
  const { getOptionByDriver } = useDeviceTypeOptions()

  const {
    rows: rules,
    isPending,
  } = useListAll<HardwareRule>([QUERIES.HARDWARE_RULE_LIST], "/compliance/hardware-rules", {
    family__ic: pagination.query?.trim(),
    sort: "id",
    view: "full",
  })

  const isSearching = Boolean(pagination.query?.trim())

  const columns = useMemo(
    () => [
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
        size: 20000,
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
        size: 20000,
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
        size: 20000,
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
        size: 20000,
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
      columnHelper.accessor("endOfSale", {
        cell: (info) => {
          const value = info.getValue()
          return <Text>{value ? formatDate(value) : t("common.nA")}</Text>
        },
        header: t("compliance.hardware.endOfSale"),
        size: 10000,
      }),
      columnHelper.accessor("endOfLife", {
        cell: (info) => {
          const value = info.getValue()
          return <Text>{value ? formatDate(value) : t("common.nA")}</Text>
        },
        header: t("compliance.hardware.endOfLife"),
        size: 10000,
      }),
      columnHelper.accessor((rule) => rule, {
        cell(info) {
          const rule = info.getValue()
          return (
            <Protected minLevel={Level.ReadWrite}>
              <TableButtonStack>
                <Tooltip content={t("common.edit")}>
                  <EditHardwareRuleTrigger rule={rule}>
                    <IconButton variant="frame" aria-label={t("policy.rule.editThe")}>
                      <LuSquarePen />
                    </IconButton>
                  </EditHardwareRuleTrigger>
                </Tooltip>
                <Tooltip content={t("common.remove")}>
                  <RemoveHardwareRuleTrigger rule={rule}>
                    <IconButton variant="frame" aria-label={t("policy.rule.removeThe")}>
                      <LuTrash />
                    </IconButton>
                  </RemoveHardwareRuleTrigger>
                </Tooltip>
              </TableButtonStack>
            </Protected>
          )
        },
        id: "actions",
        header: "",
        enableSorting: false,
        meta: {
          align: "right",
        },
        minSize: 80,
        size: 200,
      }),
    ],
    [t, formatDate, getOptionByDriver]
  )

  return (
    <Stack gap="6" p="9" flex="1" overflow="auto">
      <Heading as="h1" fontSize="4xl">
        {t("compliance.hardware.supportStatus")}
      </Heading>
      <Text color="fg.muted">{t("compliance.hardware.howItWorks")}</Text>
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
            <AddHardwareRuleTrigger>
              <Button variant="primary">
                <LuPlus />
                {t("policy.rule.add")}
              </Button>
            </AddHardwareRuleTrigger>
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
          {rules.length > 0 ? (
            <DataTable columns={columns} data={rules} loading={isPending} />
          ) : isSearching ? (
            <Text>{t("common.noResults")}</Text>
          ) : (
            <EmptyResult
              title={t("compliance.hardware.noRule")}
              description={t("compliance.hardware.canAddRule")}
            >
              <Protected minLevel={Level.ReadWrite}>
                <AddHardwareRuleTrigger>
                  <Button variant="outline">
                    <LuPlus />
                    {t("policy.rule.add")}
                  </Button>
                </AddHardwareRuleTrigger>
              </Protected>
            </EmptyResult>
          )}
        </>
      )}
    </Stack>
  )
}
