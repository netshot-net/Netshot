import type { HardwareSupportStat } from "@/api/report"
import type { GroupedHardwareSupportStat } from "@/types"

export function groupStatByDate(stats: HardwareSupportStat[]) {
  if (!Array.isArray(stats)) {
    return []
  }

  const output: GroupedHardwareSupportStat[] = []

  for (const stat of stats) {
    const item = output.find((item) => item.date === stat.date)

    if (item) {
      item.eos += stat.type === "eos" ? stat.deviceCount : 0
      item.eol += stat.type === "eol" ? stat.deviceCount : 0
    } else {
      output.push({
        date: stat.date,
        eos: stat.type === "eos" ? stat.deviceCount : 0,
        eol: stat.type === "eol" ? stat.deviceCount : 0,
      })
    }
  }

  return output.sort((a, b) => a.date - b.date)
}
