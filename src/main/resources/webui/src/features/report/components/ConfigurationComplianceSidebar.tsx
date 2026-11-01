import { Box, Separator, Skeleton, Spacer, Stack, Tag, Text, useToken } from "@chakra-ui/react"
import { ChartConfiguration } from "chart.js"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router"

import { Chart, Sidebar } from "@/components"

import { useGroupConfigComplianceStats } from "../hooks"
import ConfigurationCompliantSidebarList from "./ConfigurationComplianceSidebarList"
import ConfigurationComplianceSidebarSearch from "./ConfigurationComplianceSidebarSearch"

function ConfigurationComplianceGlobalChart() {
  const { t } = useTranslation()
  const [compliantColor] = useToken("colors", "green.500")
  const [nonCompliantColor] = useToken("colors", "red.500")
  const params = useParams<{
    id: string
  }>()

  const { stats, isPending } = useGroupConfigComplianceStats(params.id ? +params.id : undefined)

  const count = useMemo(() => {
    const output = {
      total: 0,
      compliant: 0,
      nonCompliant: 0,
    }

    if (!stats.length) {
      return output
    }

    output.total = stats.reduce((prev, current) => {
      prev += current.deviceCount
      return prev
    }, 0)

    output.compliant = stats.reduce((prev, current) => {
      prev += current.compliantDeviceCount
      return prev
    }, 0)

    output.nonCompliant = output.total - output.compliant

    return output
  }, [stats])

  const config = useMemo(() => {
    const labels = [t("compliance.compliant"), t("compliance.nonCompliant")]
    const data = [count.compliant, count.nonCompliant]
    const backgroundColor = [compliantColor, nonCompliantColor]

    return {
      type: "doughnut",
      data: {
        labels,
        datasets: [
          {
            data,
            backgroundColor,
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        cutout: "70%",
        scales: {
          x: {
            display: false,
          },
          y: {
            display: false,
          },
        },
      },
    } as ChartConfiguration<"doughnut">
  }, [t, count, compliantColor, nonCompliantColor])

  return (
    <Stack gap="5" p="5">
      <Stack w="100%" alignItems="center" h="140px">
        <Skeleton loading={!!isPending} h="140px" borderRadius="full">
          <Chart w="100%" config={config} />
        </Skeleton>
      </Stack>
      <Stack gap="2" cursor="pointer">
        <Stack direction="row" alignItems="center" gap="3">
          <Skeleton loading={!!isPending}>
            <Box w="14px" h="14px" borderRadius="4px" bg={nonCompliantColor} />
          </Skeleton>
          <Skeleton loading={!!isPending}>
            <Text>{t("compliance.nonCompliant")}</Text>
          </Skeleton>

          <Spacer />
          <Skeleton loading={!!isPending}>
            <Tag.Root bg="red.50" color="red.900">
              {count.nonCompliant}
            </Tag.Root>
          </Skeleton>
        </Stack>
        <Separator />
        <Stack direction="row" alignItems="center" gap="3">
          <Skeleton loading={!!isPending}>
            <Box w="14px" h="14px" borderRadius="4px" bg={compliantColor} />
          </Skeleton>
          <Skeleton loading={!!isPending}>
            <Text>{t("compliance.compliant")}</Text>
          </Skeleton>

          <Spacer />
          <Skeleton loading={!!isPending}>
            <Tag.Root bg="green.50" color="green.900">
              {count.compliant}
            </Tag.Root>
          </Skeleton>
        </Stack>
      </Stack>
    </Stack>
  )
}

export default function ConfigurationComplianceSidebar() {
  return (
    <Sidebar>
      <ConfigurationComplianceSidebarSearch />
      <Separator />
      <ConfigurationCompliantSidebarList />
      <Separator />
      <ConfigurationComplianceGlobalChart />
    </Sidebar>
  )
}
