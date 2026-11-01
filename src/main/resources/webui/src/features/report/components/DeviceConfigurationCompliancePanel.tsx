import { DataTable, EntityLink } from "@/components"
import { LuChevronDown } from "react-icons/lu"
import { DeviceBadge, DeviceConfigComplianceBadge } from "@/components/entity"
import { ComplianceResult } from "@/api/report"
import { DeviceNetworkClass } from "@/types"
import { useLocalization } from "@/i18n"
import { IconButton, Separator, Stack, Text } from "@chakra-ui/react"
import { createColumnHelper } from "@tanstack/react-table"
import { useCallback, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"

export type DeviceConfigurationPanelProps = {
  results: ComplianceResult[]
}

const columnHelper = createColumnHelper<ComplianceResult>()

export default function DeviceConfigurationCompliancePanel(props: DeviceConfigurationPanelProps) {
  const { results } = props
  const device = results[0]?.device
  const name = device?.name ?? ""
  const { t } = useTranslation()
  const { formatDateTime } = useLocalization()
  const [isExpanded, setIsExpanded] = useState<boolean>(false)

  const toggleExpand = useCallback(() => {
    setIsExpanded((prev) => !prev)
  }, [])

  const columns = useMemo(
    () => [
      columnHelper.accessor((row) => row.result === "CONFORMING", {
        id: "configCompliant",
        cell: (info) => <DeviceConfigComplianceBadge compliant={info.getValue()} />,
        header: t("common.status"),
        enableSorting: true,
      }),
      columnHelper.accessor((row) => row.policy?.display ?? row.policy?.name ?? "", {
        id: "policyName",
        cell: (info) => (
          <EntityLink
            to={`/app/compliance/config/${info.row.original.policy?.id}`}
            textDecoration="none"
            _hover={{ color: "green.600" }}
          >
            {info.getValue()}
          </EntityLink>
        ),
        header: t("policy.label"),
        enableSorting: true,
      }),
      columnHelper.accessor((row) => row.rule?.display ?? row.rule?.name ?? "", {
        id: "ruleName",
        cell: (info) => (
          <EntityLink
            to={`/app/compliance/config/${info.row.original.policy?.id}/${info.row.original.rule?.id}`}
            textDecoration="none"
            _hover={{ color: "green.600" }}
          >
            {info.getValue()}
          </EntityLink>
        ),
        header: t("policy.rule.label"),
        enableSorting: true,
      }),
      columnHelper.accessor("checkDate", {
        cell: (info) => {
          const checkDate = info.getValue()
          return <Text>{checkDate ? formatDateTime(checkDate) : t("common.nA")}</Text>
        },
        header: t("time.testDateTime"),
        enableSorting: true,
      }),
    ],
    [t, formatDateTime]
  )

  return (
    <Stack borderWidth="1px" borderColor="grey.100" borderRadius="2xl" bg="white" key={name} gap="0">
      <Stack
        direction="row"
        gap="3"
        alignItems="center"
        p="3"
        onClick={toggleExpand}
        cursor="pointer"
      >
        <IconButton
          size="sm"
          variant="ghost"
          colorPalette="green"
          aria-label={t("common.open")}
          css={{
            transform: isExpanded ? "" : "rotate(-90deg)",
          }}
        >
          <LuChevronDown />
        </IconButton>
        <DeviceBadge networkClass={device?.networkClass as DeviceNetworkClass | undefined}>
          <Link
            to={`/app/devices/${device?.id}/compliance?open=config`}
            onClick={(e) => e.stopPropagation()}
          >
            {name}
          </Link>
        </DeviceBadge>
      </Stack>
      {isExpanded && (
        <>
          <Separator />
          <Stack direction="column" gap="3" pb="1">
            <DataTable columns={columns} data={results} border="none" borderRadius="0" />
          </Stack>
        </>
      )}
    </Stack>
  )
}
