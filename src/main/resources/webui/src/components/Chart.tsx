import { Box, BoxProps, useToken } from "@chakra-ui/react"
import NativeChart, { ChartConfiguration, ChartTypeRegistry } from "chart.js/auto"
import mergeWith from "lodash.mergewith"
import { useLayoutEffect, useMemo, useRef } from "react"

export type ChartProps<T extends keyof ChartTypeRegistry = "line"> = {
  config: ChartConfiguration<T>
} & BoxProps

export default function Chart<T extends keyof ChartTypeRegistry = "line">(props: ChartProps<T>) {
  const { config, ...other } = props
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const chartRef = useRef<NativeChart>(null)
  const [green500] = useToken("colors", "green.500")
  const [greyColor] = useToken("colors", "grey.400")
  const [grey100] = useToken("colors", "grey.100")
  const [bodyFont] = useToken("fonts", "body")

  const defaultConfig = useMemo(() => {
    return {
      type: "line",
      data: {
        labels: [],
        datasets: [
          {
            backgroundColor: green500,
            // borderColor: green500,
            data: [],
            borderWidth: 1,
            cubicInterpolationMode: "monotone",
            tension: 0.3,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        interaction: {
          axis: "xy",
          mode: "nearest",
          intersect: false,
        },
        scales: {
          x: {
            ticks: {
              padding: 12,
              color: greyColor,
              font: {
                family: bodyFont,
                weight: 500,
                size: 12,
              },
            },
            grid: {
              color: grey100,
              // tickLength: 0,
              // drawBorder: false,
              lineWidth: 1,
            },
          },
          y: {
            min: 0,
            ticks: {
              precision: 0,
              //display: false,
            },
            grid: {
              // tickLength: 0,
              // drawBorder: false,
              lineWidth: 1,
            },
          },
        },
        plugins: {
          legend: {
            display: false,
          },
          /* tooltip: {
            callbacks: {
              title: () => null,
            },
          }, */
        },
      },
    } as ChartConfiguration
  }, [green500, grey100, greyColor, bodyFont])

  useLayoutEffect(() => {
    if (!canvasRef.current) return

    chartRef.current = new NativeChart(canvasRef.current, mergeWith({}, defaultConfig, config))

    return () => {
      chartRef.current?.destroy()
      chartRef.current = null
    }
  }, [canvasRef, chartRef, config, defaultConfig])

  return (
    <Box {...other} asChild>
      <canvas ref={canvasRef} />
    </Box>
  )
}
