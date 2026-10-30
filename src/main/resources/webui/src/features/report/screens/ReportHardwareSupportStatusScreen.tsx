import api from "@/api"
import { Chart, DataTable } from "@/components"
import { DomainSelect } from "@/features/administration/components"
import { TreeGroupSelector } from "@/features/group"
import { Tooltip } from "@/components/ui/tooltip"
import { LuFilter, LuFilterX, LuRefreshCcw } from "react-icons/lu"
import { GroupedHardwareSupportStat, HardwareSupportStatType } from "@/types"
import { groupStatByDate } from "@/utils"
import {
  Button,
  Heading,
  IconButton,
  Menu,
  Portal,
  Skeleton,
  Spacer,
  Stack,
  Text,
  useToken,
} from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { CellContext, createColumnHelper } from "@tanstack/react-table"
import { ChartConfiguration } from "chart.js/auto"
import { endOfMonth, today, toZoned } from "@internationalized/date"
import { useCallback, useMemo, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useSearchParams } from "react-router"
import { HardwareDeviceListTrigger } from "../components"
import { QUERIES } from "../constants"
import { useLocalization } from "@/i18n"

const columnHelper = createColumnHelper<GroupedHardwareSupportStat>()

type FilterForm = {
  domain: number | null
  group: number | null
}

const DEFAULT_FILTER: FilterForm = {
  domain: null,
  group: null,
}

export default function ReportHardwareSupportStatusScreen() {
  const { t } = useTranslation()
  const { formatDate, formatMonthYear, timezone } = useLocalization()
  const [green500] = useToken("colors", "green.500")
  const [bronze500] = useToken("colors", "bronze.500")
  const [grey500] = useToken("colors", "grey.500")

  const [searchParams, setSearchParams] = useSearchParams()
  const [filterOpen, setFilterOpen] = useState(false)
  const [filter, setFilter] = useState<FilterForm>(() => {
    const domainParam = searchParams.get("domain")
    const groupParam = searchParams.get("group")
    return {
      domain: domainParam ? +domainParam : DEFAULT_FILTER.domain,
      group: groupParam ? +groupParam : DEFAULT_FILTER.group,
    }
  })
  const form = useForm<FilterForm>({ defaultValues: filter })

  const isFiltered = filter.domain != null || filter.group != null

  const {
    data: stats,
    isPending,
    isFetching,
    refetch,
  } = useQuery({
    queryKey: [QUERIES.HARDWARE_SUPPORT_STATS, filter.domain, filter.group],
    queryFn: async () =>
      api.report.getAllHardwareSupportStats({
        domain: filter.domain != null ? [filter.domain] : undefined,
        group: filter.group != null ? [filter.group] : undefined,
      }),
  })

  function applyFilter(values: FilterForm) {
    setFilter(values)
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (values.domain != null) next.set("domain", String(values.domain))
        else next.delete("domain")
        if (values.group != null) next.set("group", String(values.group))
        else next.delete("group")
        return next
      },
      { replace: true }
    )
    setFilterOpen(false)
  }

  function resetFilter() {
    setFilter(DEFAULT_FILTER)
    form.reset(DEFAULT_FILTER)
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.delete("domain")
        next.delete("group")
        return next
      },
      { replace: true }
    )
    setFilterOpen(false)
  }

  const config = useMemo(() => {
    if (!Array.isArray(stats)) {
      return null
    }

    const labels: string[] = []
    const showTick: boolean[] = []
    const datas: {
      eol: number[]
      eos: number[]
      max: number[]
    } = {
      eol: [],
      eos: [],
      max: [],
    }

    const counts = {
      eos: 0,
      eol: 0,
    }

    const max = {
      eos: 0,
      eol: 0,
      eox: 0,
    }
    const tz = timezone
    const baseMonth = today(tz).set({ day: 1 })

    for (const stat of stats) {
      if (stat.type === HardwareSupportStatType.Eos) {
        max.eos += stat.deviceCount
      } else if (stat.type === HardwareSupportStatType.Eol) {
        max.eol += stat.deviceCount
      }
    }

    max.eox = max.eos > max.eol ? max.eos : max.eol

    for (let m = -4 * 12; m < 8 * 12; m++) {
      const month = baseMonth.add({ months: m })
      const monthStart = toZoned(month, tz).toDate().getTime()
      const monthEnd = toZoned(endOfMonth(month), tz).set({ hour: 23, minute: 59, second: 59, millisecond: 999 }).toDate().getTime()

      for (const stat of stats) {
        if (stat.eoxDate >= monthStart && stat.eoxDate < monthEnd) {
          if (stat.type === HardwareSupportStatType.Eos) {
            counts.eos += stat.deviceCount
          } else if (stat.type === HardwareSupportStatType.Eol) {
            counts.eol += stat.deviceCount
          }
        }
      }

      labels.push(formatMonthYear(toZoned(month, tz).toDate()))
      showTick.push(month.month === 1)
      datas.eos.push(counts.eos)
      datas.eol.push(counts.eol)
      datas.max.push(max.eox)
    }

    return {
      type: "line",
      data: {
        labels,
        datasets: [
          {
            label: t("compliance.hardware.endOfLifeDevices"),
            backgroundColor: bronze500,
            data: datas.eol,
            borderWidth: 1,
            cubicInterpolationMode: "monotone",
            tension: 0.3,
          },
          {
            label: t("compliance.hardware.endOfSaleDevices"),
            backgroundColor: grey500,
            data: datas.eos,
            borderWidth: 1,
            cubicInterpolationMode: "monotone",
            tension: 0.3,
          },
          {
            label: t("report.totalDevices"),
            backgroundColor: green500,
            data: datas.max,
            borderWidth: 1,
            cubicInterpolationMode: "monotone",
            tension: 0.3,
          },
        ],
      },
      options: {
        maintainAspectRatio: false,
        bezierCurve: false,
        pointDot: false,
        scaleShowGridLines: false,
        scaleOverride: true,
        scaleSteps: 10,
        scaleStepWidth: Math.ceil(max.eox / 10),
        scaleStartValue: 0,
        scales: {
          x: {
            ticks: {
              autoSkip: false,
              callback: (_value, index) => (showTick[index] ? labels[index] : ""),
            },
          },
          y: {
            min: 0,
            maxTicksLimit: 10,
            suggestedMax: max.eox * 1.2,
          },
        },
      },
    } as ChartConfiguration
  }, [stats, t, green500, bronze500, grey500, formatMonthYear, timezone])

  const getFormattedCount = useCallback(
    (info: CellContext<GroupedHardwareSupportStat, number>) => {
      const count = info.getValue()
      return t("device.device", { count })
    },
    [t]
  )

  const columns = useMemo(
    () => [
      columnHelper.accessor("date", {
        cell: (info) => (
          <Text>
            {info.getValue()
              ? formatDate(info.getValue())
              : t("common.never")}
          </Text>
        ),
        header: t("time.date"),
      }),
      columnHelper.accessor("eos", {
        cell: (info) => {
          if (info.getValue() === 0) {
            return null
          }

          return (
            <HardwareDeviceListTrigger
              type="eos"
              date={info.row.original.date || 0}
              domain={filter.domain != null ? [filter.domain] : undefined}
              group={filter.group != null ? [filter.group] : undefined}
            >
              <Button variant="plain" textDecoration="underline">
                +{getFormattedCount(info)}
              </Button>
            </HardwareDeviceListTrigger>
          )
        },
        header: t("compliance.hardware.endOfSale"),
      }),
      columnHelper.accessor("eol", {
        cell: (info) => {
          if (info.getValue() === 0) {
            return null
          }

          return (
            <HardwareDeviceListTrigger
              type="eol"
              date={info.row.original.date || 0}
              domain={filter.domain != null ? [filter.domain] : undefined}
              group={filter.group != null ? [filter.group] : undefined}
            >
              <Button variant="plain" textDecoration="underline">
                +{getFormattedCount(info)}
              </Button>
            </HardwareDeviceListTrigger>
          )
        },
        header: t("compliance.hardware.endOfLife"),
      }),
    ],
    [t, getFormattedCount, filter.domain, filter.group, formatDate]
  )

  const data = useMemo(() => groupStatByDate(stats ?? []), [stats])

  return (
    <Stack gap="8" p="9" flex="1" overflowY="auto">
      <Stack direction="row" alignItems="center" gap="3">
        <Heading as="h1" fontSize="4xl">
          {t("compliance.hardware.supportStatus")}
        </Heading>
        <Tooltip content={t("common.refresh")}>
          <IconButton
            aria-label={t("common.refresh")}
            variant="ghost"
            size="sm"
            color="fg.muted"
            onClick={() => refetch()}
            loading={isFetching}
          >
            <LuRefreshCcw />
          </IconButton>
        </Tooltip>
        <Spacer />
        <Menu.Root
          open={filterOpen}
          onOpenChange={(e) => {
            setFilterOpen(e.open)
            if (!e.open) {
              form.reset(filter)
            }
          }}
        >
          <Menu.Trigger asChild>
            <Button variant="primary">
              {isFiltered ? <LuFilterX /> : <LuFilter />}
              {t("common.filters")}
            </Button>
          </Menu.Trigger>
          <Portal>
            <Menu.Positioner>
              <Menu.Content w="300px" p="3">
                <Stack gap="4" asChild>
                  <form onSubmit={form.handleSubmit(applyFilter)}>
                    <DomainSelect control={form.control} name="domain" withAny />
                    <TreeGroupSelector control={form.control} name="group" withAny />
                    <Stack direction="row" gap="2">
                      <Button type="button" flex="1" onClick={() => setFilterOpen(false)}>
                        {t("common.cancel")}
                      </Button>
                      <Button type="button" flex="1" onClick={resetFilter}>
                        {t("common.reset")}
                      </Button>
                      <Button type="submit" variant="primary" flex="1">
                        {t("common.apply")}
                      </Button>
                    </Stack>
                  </form>
                </Stack>
              </Menu.Content>
            </Menu.Positioner>
          </Portal>
        </Menu.Root>
      </Stack>
      <Stack gap="5">
        <Heading as="h4" fontSize="2xl">
          {t("common.overview")}
        </Heading>

        {isPending ? (
          <Skeleton height="300px" />
        ) : (
          <Stack h="300px">
            <Chart config={config!} />
          </Stack>
        )}
      </Stack>
      <Stack gap="5" flex="1" overflowY="auto">
        <Heading as="h4" fontSize="2xl">
          {t("compliance.hardware.milestone")}
        </Heading>
        {isPending ? (
          <Stack gap="3">
            <Skeleton h="60px" />
            <Skeleton h="60px" />
            <Skeleton h="60px" />
            <Skeleton h="60px" />
          </Stack>
        ) : (
          <DataTable data={data} columns={columns} />
        )}
      </Stack>
    </Stack>
  )
}
