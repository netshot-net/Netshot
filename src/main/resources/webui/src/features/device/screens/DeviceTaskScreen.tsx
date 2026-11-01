import { Heading, Stack } from "@chakra-ui/react"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router"

import { Schemas } from "@/api/client"
import TaskTable from "@/features/task/components/TaskTable"
import TaskTreeModeToggle from "@/features/task/components/TaskTreeModeToggle"
import { useList } from "@/hooks/useList"
import { LightTask, SchedulePriority, TaskScheduleMode, TaskScheduleType, TaskStatus } from "@/types"

import { QUERIES } from "../constants"

const TASKS_PAGE_SIZE = 20

/**
 * The task table still takes the compact task shape: until it takes the tasks of the API as they are, adapt them.
 */
function toTableTask(task: Schemas["Task"]): LightTask {
  return {
    id: task.id ?? 0,
    type: task.type ?? "",
    status: task.status as TaskStatus,
    author: task.author ?? "",
    target: task.target ?? "",
    comments: task.comments ?? "",
    creationDate: task.creationDate?.getTime() ?? 0,
    changeDate: task.changeDate?.getTime() ?? 0,
    executionDate: task.executionDate?.getTime() ?? 0,
    scheduleReference: task.scheduleReference?.getTime() ?? 0,
    scheduleType: task.scheduleType as TaskScheduleType,
    scheduleFactor: task.scheduleFactor ?? 1,
    priority: (task.priority ?? 5) as SchedulePriority,
    runnerId: task.runnerId ?? "",
    debugEnabled: !!task.debugEnabled,
    deviceId: task.device?.id,
    deviceGroupId: task.group?.id,
    parentTaskId: task.parentTask?.id,
    childOrder: task.childOrder ?? undefined,
    scheduleMode: task.scheduleMode as TaskScheduleMode | undefined,
    stopOnFailure: task.stopOnFailure ?? undefined,
  }
}

export default function DeviceTaskScreen() {
  const params = useParams<{ id: string }>()
  const { t } = useTranslation()

  const { rows, isPending, onBottomReached } = useList<Schemas["Task"]>(
    [QUERIES.DEVICE_TASKS, params.id],
    `/devices/${params.id}/tasks`,
    { view: "full", omit: "log,parameters" },
    { limit: TASKS_PAGE_SIZE }
  )
  const tasks = useMemo(() => rows.map(toTableTask), [rows])

  return (
    <Stack gap="6" flex="1" overflow="auto">
      <Stack direction="row" alignItems="center" justifyContent="space-between" gap="3">
        <Heading size="md">{t("device.latestTasks")}</Heading>
        <TaskTreeModeToggle />
      </Stack>
      <TaskTable
        rows={tasks}
        isPending={isPending}
        showTarget={false}
        emptyDescription={t("device.noTask")}
        onBottomReached={onBottomReached}
      />
    </Stack>
  )
}
