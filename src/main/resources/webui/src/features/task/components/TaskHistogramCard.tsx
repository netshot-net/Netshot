import { Chart } from "@/components"
import { TASK_STATUS_CONFIG } from "./TaskStatusBadge"
import { useLocalization } from "@/i18n"
import { Badge, Box, Icon, Stack, Text, useToken } from "@chakra-ui/react"
import NativeChart, { ChartConfiguration, Plugin } from "chart.js/auto"
import { PointerEvent, useMemo, useRef } from "react"
import { useTranslation } from "react-i18next"
import { useSearchParams } from "react-router"
import { FINAL_STATUS_KEYS } from "../constants"
import { useTaskStats } from "../hooks"
import { useTaskHistoryFilterStore } from "../stores/useTaskHistoryFilterStore"

export default function TaskHistogramCard() {
  const { t } = useTranslation()
  const { formatDate, formatHourMinute } = useLocalization()
  const { bins, statusCounts, unit } = useTaskStats()
  const statusSel = useTaskHistoryFilterStore((s) => s.statusSel)
  const toggleStatus = useTaskHistoryFilterStore((s) => s.toggleStatus)
  const setRange = useTaskHistoryFilterStore((s) => s.setRange)
  const [, setSearchParams] = useSearchParams()
  const [selectionColor] = useToken("colors", "green.50")

  // Horizontal drag selection on the chart, as pixel positions along the x axis
  // (kept in a ref and painted by a plugin, so dragging doesn't rebuild the chart).
  const wrapperRef = useRef<HTMLDivElement>(null)
  const selectionRef = useRef<{ start: number; end: number } | null>(null)

  const statusColorTokenNames = useMemo(
    () => FINAL_STATUS_KEYS.map((status) => `${TASK_STATUS_CONFIG[status].colorPalette}.500`),
    []
  )
  const colorTokens = useToken("colors", statusColorTokenNames)

  // Bin start labels: the date alone (day buckets), or the time with the date on a line
  // above it on the first label and wherever the day changes from the previous shown
  // label (only every other label is shown).
  const binLabels = useMemo<(string | string[])[]>(() => {
    if (unit.labelPrecision === "day") return bins.map((bin) => formatDate(bin.from))
    return bins.map((bin, index) => {
      const day = formatDate(bin.from)
      const isNewDay = index < 2 || formatDate(bins[index - 2].from) !== day
      return isNewDay ? [day, formatHourMinute(bin.from)] : formatHourMinute(bin.from)
    })
  }, [unit, bins, formatDate, formatHourMinute])

  const binTitles = useMemo(
    () =>
      bins.map((bin) =>
        unit.labelPrecision === "day"
          ? formatDate(bin.from)
          : `${formatDate(bin.from)} ${formatHourMinute(bin.from)}`
      ),
    [unit, bins, formatDate, formatHourMinute]
  )

  const selectionPlugin = useMemo<Plugin>(
    () => ({
      id: "taskHistogramSelection",
      beforeDatasetsDraw(chart) {
        const selection = selectionRef.current
        if (!selection) return
        const { top, bottom } = chart.chartArea
        const left = Math.min(selection.start, selection.end)
        const right = Math.max(selection.start, selection.end)
        chart.ctx.save()
        chart.ctx.fillStyle = selectionColor
        chart.ctx.fillRect(left, top, right - left, bottom - top)
        chart.ctx.restore()
      },
    }),
    [selectionColor]
  )

  function getChart() {
    const canvas = wrapperRef.current?.querySelector("canvas")
    return canvas ? NativeChart.getChart(canvas) : undefined
  }

  function toChartX(chart: NativeChart, event: PointerEvent) {
    const { left, right } = chart.chartArea
    const x = event.clientX - chart.canvas.getBoundingClientRect().left
    return Math.min(Math.max(x, left), right)
  }

  function isInChartArea(chart: NativeChart, event: PointerEvent) {
    const { left, right, top, bottom } = chart.chartArea
    const rect = chart.canvas.getBoundingClientRect()
    const x = event.clientX - rect.left
    const y = event.clientY - rect.top
    return x >= left && x <= right && y >= top && y <= bottom
  }

  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    const chart = getChart()
    if (event.button !== 0 || !chart || bins.length === 0 || !isInChartArea(chart, event)) return
    event.currentTarget.setPointerCapture(event.pointerId)
    const x = toChartX(chart, event)
    selectionRef.current = { start: x, end: x }
  }

  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    const chart = getChart()
    if (!chart) return
    const selection = selectionRef.current
    event.currentTarget.style.cursor = selection || isInChartArea(chart, event) ? "col-resize" : ""
    if (!selection) return
    selection.end = toChartX(chart, event)
    chart.draw()
  }

  function onPointerUp(event: PointerEvent<HTMLDivElement>) {
    const chart = getChart()
    const selection = selectionRef.current
    selectionRef.current = null
    if (!chart || !selection) return
    chart.draw()
    // A plain click (no real drag) doesn't change the range.
    if (Math.abs(selection.end - selection.start) < 3) return
    // Snap the selection to the bins it touches.
    const toBinIndex = (x: number) =>
      Math.min(Math.max(Math.round(chart.scales.x.getValueForPixel(x) ?? 0), 0), bins.length - 1)
    const startIndex = toBinIndex(Math.min(selection.start, selection.end))
    const endIndex = toBinIndex(Math.max(selection.start, selection.end))
    const from = bins[startIndex].from
    const to = bins[endIndex].to
    setRange(from, to)
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.set("from", String(from))
        next.set("to", String(to))
        return next
      },
      { replace: true }
    )
    event.currentTarget.style.cursor = ""
  }

  function onPointerCancel() {
    selectionRef.current = null
    getChart()?.draw()
  }

  const config = useMemo<ChartConfiguration>(() => {
    const visibleStatuses = FINAL_STATUS_KEYS.filter(
      (status) => statusSel.length === 0 || statusSel.includes(status)
    )
    const datasets = visibleStatuses.map((status) => ({
      label: t(TASK_STATUS_CONFIG[status].labelKey),
      data: bins.map((bin) => bin.countByStatus[status] ?? 0),
      backgroundColor: colorTokens[FINAL_STATUS_KEYS.indexOf(status)],
      borderWidth: 0,
      stack: "tasks",
    }))

    return {
      type: "bar",
      data: { labels: binLabels, datasets },
      plugins: [selectionPlugin],
      options: {
        maintainAspectRatio: false,
        scales: {
          x: {
            stacked: true,
            grid: { display: false },
            ticks: {
              autoSkip: false,
              callback: (_value, index) => (index % 2 === 0 ? binLabels[index] : ""),
            },
          },
          y: { stacked: true, min: 0 },
        },
        plugins: {
          tooltip: {
            callbacks: {
              title: (items) => binTitles[items[0]?.dataIndex],
            },
          },
        },
      },
    } as ChartConfiguration
  }, [bins, statusSel, colorTokens, t, binLabels, binTitles, selectionPlugin])

  return (
    <Stack gap="3" bg="white" borderWidth="1px" borderColor="grey.100" borderRadius="2xl" p="5">
      <Stack direction="row" alignItems="center" gap="4" flexWrap="wrap">
        <Stack direction="row" gap="2">
          {FINAL_STATUS_KEYS.map((status) => {
            const on = statusSel.length === 0 || statusSel.includes(status)
            const statusConfig = TASK_STATUS_CONFIG[status]
            return (
              <Badge
                key={status}
                onClick={() => toggleStatus(status)}
                variant="surface"
                colorPalette={statusConfig.colorPalette}
                cursor="pointer"
                opacity={on ? 1 : 0.45}
                display="inline-flex"
                alignItems="center"
                gap="1"
              >
                <Icon size="sm">{statusConfig.icon}</Icon>
                {t(statusConfig.labelKey)}
                <Text as="span" color="grey.400">
                  {statusCounts[status] ?? 0}
                </Text>
              </Badge>
            )
          })}
        </Stack>
      </Stack>
      <Box
        ref={wrapperRef}
        h="210px"
        userSelect="none"
        touchAction="pan-y"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerCancel}
      >
        <Chart config={config} h="100%" />
      </Box>
    </Stack>
  )
}
