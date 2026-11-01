import { TASK_STATUS_CONFIG } from "./TaskStatusBadge"
import { TaskStatus } from "@/types"
import { Box, Icon, Progress, SimpleGrid, Stack, Text } from "@chakra-ui/react"
import { useTranslation } from "react-i18next"
import { LuHistory } from "react-icons/lu"
import { FINAL_STATUS_KEYS } from "../constants"
import { useRecentCompletedCounts, useTaskSummary } from "../hooks"
import { useActiveTaskFilterStore } from "../stores/useActiveTaskFilterStore"

function StatusIcon({ status }: { status: TaskStatus }) {
  const config = TASK_STATUS_CONFIG[status]
  return (
    <Icon boxSize="6" color={`${config?.colorPalette}.500`} flexShrink={0}>
      {config?.icon}
    </Icon>
  )
}

export default function TaskLiveStats() {
  const { t } = useTranslation()
  const statusSel = useActiveTaskFilterStore((s) => s.statusSel)
  const toggleStatus = useActiveTaskFilterStore((s) => s.toggleStatus)
  const toggleStatuses = useActiveTaskFilterStore((s) => s.toggleStatuses)

  const { countByStatus, threadCount } = useTaskSummary()
  const recentCounts = useRecentCompletedCounts()

  const runningCount = countByStatus[TaskStatus.Running] ?? 0
  const waitingCount = countByStatus[TaskStatus.Waiting] ?? 0
  const scheduledCount = countByStatus[TaskStatus.Scheduled] ?? 0
  const freeSlots = Math.max(0, threadCount - runningCount)

  const waitingCaption =
    waitingCount === 0
      ? t("task.queueEmpty")
      : freeSlots > 0
        ? t("task.queueNextInLine")
        : t("task.queueBlocked")

  const scheduledCaption =
    scheduledCount > 0 ? t("task.scheduledWillTrigger") : t("task.nothingScheduled")

  function tileProps(status: TaskStatus) {
    const on = statusSel.length === 0 || statusSel.includes(status)
    return {
      onClick: () => toggleStatus(status),
      cursor: "pointer" as const,
      p: "4",
      borderRadius: "xl" as const,
      borderWidth: "1px",
      borderColor: on ? "grey.100" : "grey.50",
      bg: "white",
      opacity: on ? 1 : 0.5,
      transition: "all .2s ease",
      flex: "1",
    }
  }

  function groupTileProps(statuses: TaskStatus[]) {
    const on = statusSel.length === 0 || statuses.every((status) => statusSel.includes(status))
    return {
      onClick: () => toggleStatuses(statuses),
      cursor: "pointer" as const,
      p: "4",
      borderRadius: "xl" as const,
      borderWidth: "1px",
      borderColor: on ? "grey.100" : "grey.50",
      bg: "white",
      opacity: on ? 1 : 0.5,
      transition: "all .2s ease",
      flex: "1",
    }
  }

  return (
    <SimpleGrid columns={4} gap="4">
      <Box {...tileProps(TaskStatus.Running)}>
        <Stack direction="row" alignItems="flex-start" gap="3">
          <StatusIcon status={TaskStatus.Running} />
          <Stack flex="1" gap="0">
            <Stack direction="row" alignItems="center" gap="2">
              <Text fontSize="sm" fontWeight="medium" color="grey.700" flex="1">
                {t("common.running")}
              </Text>
              <Text fontSize="md" fontWeight="bold">
                {runningCount}
              </Text>
              <Text fontSize="xs" color="grey.400">
                / {threadCount}
              </Text>
            </Stack>
            <Progress.Root
              value={runningCount}
              max={Math.max(threadCount, 1)}
              size="xs"
              mt="2"
              colorPalette="blue"
            >
              <Progress.Track>
                <Progress.Range />
              </Progress.Track>
            </Progress.Root>
            <Text fontSize="xs" color="grey.400" mt="1">
              {freeSlots > 0 ? t("task.slotsFree", { count: freeSlots }) : t("task.allThreadsBusy")}
            </Text>
          </Stack>
        </Stack>
      </Box>

      <Box {...tileProps(TaskStatus.Waiting)}>
        <Stack direction="row" alignItems="flex-start" gap="3">
          <StatusIcon status={TaskStatus.Waiting} />
          <Stack flex="1" gap="0">
            <Stack direction="row" alignItems="center" gap="2">
              <Text fontSize="sm" fontWeight="medium" color="grey.700" flex="1">
                {t("task.waitingInQueue")}
              </Text>
              <Text fontSize="md" fontWeight="bold">
                {waitingCount}
              </Text>
            </Stack>
            <Text fontSize="xs" color="grey.400" mt="1">
              {waitingCaption}
            </Text>
          </Stack>
        </Stack>
      </Box>

      <Box {...tileProps(TaskStatus.Scheduled)}>
        <Stack direction="row" alignItems="flex-start" gap="3">
          <StatusIcon status={TaskStatus.Scheduled} />
          <Stack flex="1" gap="0">
            <Stack direction="row" alignItems="center" gap="2">
              <Text fontSize="sm" fontWeight="medium" color="grey.700" flex="1">
                {t("task.scheduledFuture")}
              </Text>
              <Text fontSize="md" fontWeight="bold">
                {scheduledCount}
              </Text>
            </Stack>
            <Text fontSize="xs" color="grey.400" mt="1">
              {scheduledCaption}
            </Text>
          </Stack>
        </Stack>
      </Box>

      <Box {...groupTileProps(FINAL_STATUS_KEYS)}>
        <Stack direction="row" alignItems="flex-start" gap="3">
          <Icon boxSize="6" color="grey.400" flexShrink={0}>
            <LuHistory />
          </Icon>
          <Stack flex="1" gap="0">
            <Text fontSize="sm" fontWeight="medium" color="grey.700">
              {t("task.recentlyCompleted")}
            </Text>
            <Stack direction="row" gap="3" mt="2">
              {FINAL_STATUS_KEYS.map((status) => {
                const config = TASK_STATUS_CONFIG[status]
                return (
                  <Stack key={status} direction="row" alignItems="center" gap="1">
                    <Icon boxSize="4" color={`${config?.colorPalette}.500`} flexShrink={0}>
                      {config?.icon}
                    </Icon>
                    <Text fontSize="md" fontWeight="bold">
                      {recentCounts[status] ?? 0}
                    </Text>
                  </Stack>
                )
              })}
            </Stack>
            <Text fontSize="xs" color="grey.400" mt="1">
              {t("task.recentlyCompletedCaption")}
            </Text>
          </Stack>
        </Stack>
      </Box>
    </SimpleGrid>
  )
}
