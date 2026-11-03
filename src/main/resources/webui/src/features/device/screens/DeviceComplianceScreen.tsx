import { Accordion, Badge, Button, Heading, Icon, Spacer, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { createColumnHelper } from "@tanstack/react-table"
import { useMemo } from "react"
import { Trans, useTranslation } from "react-i18next"
import { useParams, useSearchParams } from "react-router"

import { listAll } from "@/api/client"
import { Schemas } from "@/api/client"
import { AlertBox, DataTable, EntityLink, ExpandableTextCell, Protected } from "@/components"
import Search from "@/components/Search"
import { LuCircleCheck, LuTrophy } from "react-icons/lu"
import {
  DeviceComplianceResultType,
  DeviceSoftwareLevel,
  Level,
} from "@/types"
import { useLocalization } from "@/i18n"
import { usePagination, useSoftwareLevels } from "@/hooks"
import { search } from "@/utils"

import { DeviceComplianceTag } from "@/components/entity"
import DeviceSoftwareLevelBadge from "@/components/entity/DeviceSoftwareLevelBadge"
import DeviceConfigComplianceBadge from "@/components/entity/DeviceConfigComplianceBadge"
import DeviceHardwareComplianceBadge from "@/components/entity/DeviceHardwareComplianceBadge"
import DeviceComplianceTrigger from "../components/DeviceComplianceTrigger"
import { QUERIES } from "../constants"
import { useDevice } from "../contexts/device"

/** A compliance result of the device, flattened for the table. */
type ComplianceRow = {
  policyId: number
  policyName: string
  ruleId: number
  ruleName: string
  result: DeviceComplianceResultType
  comment: string
  checkDate?: Date | null
}

const columnHelper = createColumnHelper<ComplianceRow>()

function SoftwareLevelTag({ colorPalette, children }: { colorPalette?: string; children?: React.ReactNode }) {
  return (
    <Badge as="span" variant="surface" colorPalette={colorPalette} display="inline-flex" alignItems="center" gap="1" mx="1">
      <Icon size="xs" flexShrink={0}><LuTrophy /></Icon>
      {children}
    </Badge>
  )
}

export default function DeviceComplianceScreen() {
  const { t } = useTranslation()
  const { formatDate, formatDateTime } = useLocalization()
  const { device } = useDevice()
  const params = useParams<{ id: string }>()
  const { getColor: getLevelColor } = useSoftwareLevels()
  const [searchParams, setSearchParams] = useSearchParams()
  const pagination = usePagination()

  const { data = [], isPending } = useQuery({
    queryKey: [QUERIES.DEVICE_COMPLIANCE, params?.id],
    queryFn: async (): Promise<ComplianceRow[]> =>
      (
        await listAll<Schemas["DeviceComplianceResult"]>(`/devices/${params?.id}/compliance-results`, {
          view: "full",
          sort: "policyName,ruleName",
        })
      ).map((result) => ({
        policyId: result.policy?.id ?? 0,
        policyName: result.policy?.display ?? "",
        ruleId: result.rule?.id ?? 0,
        ruleName: result.rule?.display ?? "",
        result: result.result as DeviceComplianceResultType,
        comment: result.comment ?? "",
        checkDate: result.checkDate,
      })),
  })

  const isSearching = Boolean(pagination.query?.trim())

  const filteredData = useMemo(
    () => search(data, "policyName", "ruleName", "comment").with(pagination.query),
    [data, pagination.query]
  )

  const openSections = useMemo(() => {
    const open = searchParams.get("open")
    return open ? open.split(",").filter(Boolean) : []
  }, [searchParams])

  const accordionValue = isSearching ? ["config"] : openSections

  function handleAccordionChange(details: { value: string[] }) {
    if (isSearching) return

    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (details.value.length > 0) {
          next.set("open", details.value.join(","))
        } else {
          next.delete("open")
        }
        return next
      },
      { replace: true }
    )
  }

  const columns = useMemo(
    () => [
      columnHelper.accessor("policyName", {
        cell: (info) => (
          <EntityLink
            to={`/app/compliance/config/${info.row.original.policyId}`}
            textDecoration="none"
            _hover={{ color: "green.600" }}
          >
            {info.getValue()}
          </EntityLink>
        ),
        header: t("policy.label"),
        enableSorting: true,
        size: 10000,
      }),
      columnHelper.accessor("ruleName", {
        cell: (info) => (
          <EntityLink
            to={`/app/compliance/config/${info.row.original.policyId}/${info.row.original.ruleId}`}
            textDecoration="none"
            _hover={{ color: "green.600" }}
          >
            {info.getValue()}
          </EntityLink>
        ),
        header: t("policy.rule.label"),
        enableSorting: true,
        size: 10000,
      }),
      columnHelper.accessor("result", {
        cell: (info) => <DeviceComplianceTag resultType={info.getValue()} />,
        header: t("common.result"),
        enableSorting: true,
        size: 10000,
      }),
      columnHelper.accessor("comment", {
        cell: (info) => <ExpandableTextCell value={info.getValue()} title={t("common.details")} />,
        header: t("common.details"),
        enableSorting: true,
        size: 20000,
      }),
      columnHelper.accessor("checkDate", {
        cell: (info) => <Text>{info.getValue() ? formatDateTime(info.getValue()!) : t("common.nA")}</Text>,
        header: t("compliance.lastCheck"),
        enableSorting: true,
        size: 10000,
      }),
    ],
    [t, formatDateTime]
  )

  const softwareLevelColor = useMemo(() => {
    return getLevelColor(device?.softwareLevel)
  }, [device?.softwareLevel, getLevelColor])

  return (
    <Stack gap="6" flex="1">
      <Stack direction="row">
        <Search
          placeholder={t("common.searchPlaceholder")}
          onQuery={pagination.onQuery}
          onClear={pagination.onQueryClear}
          w="25%"
        />
        <Spacer />
        <Protected minLevel={Level.Operator}>
          <DeviceComplianceTrigger devices={[device]}>
            <Button alignSelf="start" variant="outline">
              <LuCircleCheck />
              {t("compliance.check")}
            </Button>
          </DeviceComplianceTrigger>
        </Protected>
      </Stack>

      <Accordion.Root multiple value={accordionValue} onValueChange={handleAccordionChange}>
        {!isSearching && (
        <>
        <Accordion.Item value="software" py="4">
          <Accordion.ItemTrigger cursor="pointer">
            <Accordion.ItemIndicator rotate="-90deg" _open={{ rotate: "0deg" }} />
            <Stack direction="row" alignItems="center" justifyContent="space-between" gap="12" flex="1">
              <Heading size="md">{t("common.software")}</Heading>
              {device?.softwareLevel && <DeviceSoftwareLevelBadge level={device.softwareLevel} />}
            </Stack>
          </Accordion.ItemTrigger>
          <Accordion.ItemContent>
            <Stack gap="4" p="4">
              <AlertBox
                type={device?.softwareLevel === DeviceSoftwareLevel.UNKNOWN ? "error" : "success"}
              >
                {device?.softwareLevel === DeviceSoftwareLevel.UNKNOWN ? (
                  <Text>
                    {t("compliance.software.versionDoesNotConform", {
                      version: device?.softwareVersion || t("common.nA"),
                    })}
                  </Text>
                ) : (
                  <Text>
                    <Trans
                      t={t}
                      i18nKey="compliance.software.conformanceLevel"
                      values={{ level: device?.softwareLevel }}
                      components={{ tag: <SoftwareLevelTag colorPalette={softwareLevelColor} /> }}
                    />
                  </Text>
                )}
              </AlertBox>
            </Stack>
          </Accordion.ItemContent>
        </Accordion.Item>

        <Accordion.Item value="hardware" py="4">
          <Accordion.ItemTrigger cursor="pointer">
            <Accordion.ItemIndicator rotate="-90deg" _open={{ rotate: "0deg" }} />
            <Stack direction="row" alignItems="center" justifyContent="space-between" gap="12" flex="1">
              <Heading size="md">{t("common.hardware")}</Heading>
              {device && (
                <DeviceHardwareComplianceBadge eol={!!device.endOfLife} eos={!!device.endOfSale} />
              )}
            </Stack>
          </Accordion.ItemTrigger>
          <Accordion.ItemContent>
            <Stack gap="3" direction="row" p="4">
              <AlertBox type={device?.endOfSale ? "warning" : "success"}>
                {device?.endOfSale ? (
                  <Text>
                    {t("compliance.hardware.endOfSaleSince", {
                      date: formatDate(device!.eosDate!),
                      module: device?.eosModule?.partNumber,
                    })}
                  </Text>
                ) : (
                  <Text>
                    {device?.eosDate
                      ? t("compliance.hardware.notEndOfSaleYetPlanned", {
                          date: formatDate(device!.eosDate!),
                          module: device?.eosModule?.partNumber,
                        })
                      : t("compliance.hardware.notEndOfSaleYet")}
                  </Text>
                )}
              </AlertBox>
              <AlertBox type={device?.endOfLife ? "warning" : "success"}>
                {device?.endOfLife ? (
                  <Text>
                    {t("compliance.hardware.endOfLifeSince", {
                      date: formatDate(device!.eolDate!),
                      module: device?.eolModule?.partNumber,
                    })}
                  </Text>
                ) : (
                  <Text>
                    {device?.eolDate
                      ? t("compliance.hardware.notEndOfLifeYetPlanned", {
                          date: formatDate(device!.eolDate!),
                          module: device?.eolModule?.partNumber,
                        })
                      : t("compliance.hardware.notEndOfLifeYet")}
                  </Text>
                )}
              </AlertBox>
            </Stack>
          </Accordion.ItemContent>
        </Accordion.Item>
        </>
        )}

        <Accordion.Item value="config" py="4">
          <Accordion.ItemTrigger cursor="pointer">
            <Accordion.ItemIndicator rotate="-90deg" _open={{ rotate: "0deg" }} />
            <Stack direction="row" alignItems="center" justifyContent="space-between" gap="12" flex="1">
              <Heading size="md">{t("device.config.label")}</Heading>
              {device && <DeviceConfigComplianceBadge compliant={!!device.compliant} />}
            </Stack>
          </Accordion.ItemTrigger>
          <Accordion.ItemContent>
            <Stack gap="4" p="4">
              {data?.length > 0 ? (
                <>
                  {!isSearching && (
                    <AlertBox type={device?.compliant ? "success" : "error"}>
                      {device?.compliant ? (
                        <Text>{t("compliance.deviceCompliantWithAll")}</Text>
                      ) : (
                        <Text>{t("compliance.deviceNotCompliantWithSome")}</Text>
                      )}
                    </AlertBox>
                  )}
                  {filteredData.length > 0 ? (
                    <DataTable columns={columns} data={filteredData} loading={isPending} />
                  ) : (
                    <Text>{t("common.noResults")}</Text>
                  )}
                </>
              ) : (
                <Text>{t("compliance.noResultForDevice")}</Text>
              )}
            </Stack>
          </Accordion.ItemContent>
        </Accordion.Item>
      </Accordion.Root>
    </Stack>
  )
}
