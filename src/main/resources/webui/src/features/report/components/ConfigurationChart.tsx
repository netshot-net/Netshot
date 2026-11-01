import { Chart } from "@/components"
import { useLocalization } from "@/i18n/useLocalization"
import { Heading, Skeleton, Stack, useToken } from "@chakra-ui/react"
import { ChartConfiguration } from "chart.js/auto"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import { useConfigChangeBins } from "../hooks"
import { useConfigChangeFilterStore } from "../stores/useConfigChangeFilterStore"

export default function ConfigurationChart() {
  const { t } = useTranslation()
  const { formatDate } = useLocalization()
  const [green500, green100] = useToken("colors", ["green.500", "green.100"])

  const { bins, isPending } = useConfigChangeBins()
  const day = useConfigChangeFilterStore((s) => s.day)
  const setDay = useConfigChangeFilterStore((s) => s.setDay)

  const config = useMemo<ChartConfiguration>(() => {
    const labels = bins.map((bin) => formatDate(bin.from))
    const data = bins.map((bin) => bin.count)
    const backgroundColor = bins.map((bin) => (day == null || bin.from === day ? green500 : green100))

    return {
      type: "bar",
      data: {
        labels,
        datasets: [
          {
            data,
            backgroundColor,
            borderWidth: 0,
            borderRadius: 4,
            maxBarThickness: 32,
          },
        ],
      },
      options: {
        maintainAspectRatio: false,
        scales: {
          x: {
            grid: { display: false },
            ticks: {
              autoSkip: false,
              callback: (_value, index) =>
                bins.length <= 15 || index % Math.ceil(bins.length / 15) === 0 ? labels[index] : "",
            },
          },
          y: {
            min: 0,
            suggestedMax: 10,
            ticks: { precision: 0 },
          },
        },
        plugins: {
          legend: { display: false },
        },
        onClick(_evt, elements) {
          if (!elements.length) return
          const bin = bins[elements[0].index]
          if (bin) setDay(bin.from)
        },
        onHover(evt, elements) {
          const target = evt.native?.target as HTMLElement
          if (target) target.style.cursor = elements.length ? "pointer" : "default"
        },
      },
    } as ChartConfiguration
  }, [bins, formatDate, day, green500, green100, setDay])

  return (
    <Stack gap="5">
      <Heading as="h4" fontSize="2xl">
        {t("device.changesOverLastDays")}
      </Heading>
      {isPending ? (
        <Skeleton height="250px" />
      ) : (
        <Stack h="250px">
          <Chart config={config} h="100%" />
        </Stack>
      )}
    </Stack>
  )
}
