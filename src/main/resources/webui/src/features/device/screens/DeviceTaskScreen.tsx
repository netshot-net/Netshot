import { Heading, Stack } from "@chakra-ui/react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router"

import { Task, TASK_LIST_FIELDS } from "@/api/task"
import TaskTable from "@/features/task/components/TaskTable"
import TaskTreeModeToggle from "@/features/task/components/TaskTreeModeToggle"
import { useList } from "@/hooks/useList"

import { QUERIES } from "../constants"

const TASKS_PAGE_SIZE = 20

export default function DeviceTaskScreen() {
  const params = useParams<{ id: string }>()
  const { t } = useTranslation()

  const { rows, isPending, onBottomReached } = useList<Task>(
    [QUERIES.DEVICE_TASKS, params.id],
    `/devices/${params.id}/tasks`,
    { fields: TASK_LIST_FIELDS },
    { limit: TASKS_PAGE_SIZE }
  )

  return (
    <Stack gap="6" flex="1" overflow="auto">
      <Stack direction="row" alignItems="center" justifyContent="space-between" gap="3">
        <Heading size="md">{t("device.latestTasks")}</Heading>
        <TaskTreeModeToggle />
      </Stack>
      <TaskTable
        rows={rows}
        isPending={isPending}
        showTarget={false}
        emptyDescription={t("device.noTask")}
        onBottomReached={onBottomReached}
      />
    </Stack>
  )
}
