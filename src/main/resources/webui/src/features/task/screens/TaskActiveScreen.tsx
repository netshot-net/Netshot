import { Protected } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { Level } from "@/types"
import { Button, Heading, IconButton, Stack, Text } from "@chakra-ui/react"
import { useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { LuPlus, LuRefreshCcw } from "react-icons/lu"
import AddTaskTrigger from "../components/AddTaskTrigger"
import TaskFilterMenu from "../components/TaskFilterMenu"
import TaskLiveStats from "../components/TaskLiveStats"
import TaskTable from "../components/TaskTable"
import TaskTreeModeToggle from "../components/TaskTreeModeToggle"
import { ALL_STATUS_KEYS } from "../constants"
import { LIVE_ROWS_LIMIT, useActiveTasks, useTaskSummary } from "../hooks"

export default function TaskActiveScreen() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const { rows, truncated, isPending, isFetching: isFetchingTasks } = useActiveTasks()
  const { isFetching: isFetchingSummary } = useTaskSummary()

  function onRefresh() {
    queryClient.invalidateQueries({
      predicate: (query) =>
        typeof query.queryKey[0] === "string" && query.queryKey[0].startsWith("task:"),
    })
  }

  return (
    <Stack gap="6" p="9" flex="1" overflow="auto">
      <Stack direction="row" alignItems="flex-start" justifyContent="space-between" gap="3">
        <Stack gap="1">
          <Stack direction="row" alignItems="center" gap="3">
            <Heading as="h1" fontSize="4xl">
              {t("task.active")}
            </Heading>
            <Tooltip content={t("common.refresh")}>
              <IconButton
                aria-label={t("common.refresh")}
                variant="ghost"
                size="sm"
                color="fg.muted"
                onClick={onRefresh}
                loading={isFetchingTasks || isFetchingSummary}
              >
                <LuRefreshCcw />
              </IconButton>
            </Tooltip>
          </Stack>
          <Text fontSize="sm" color="grey.400">
            {t("task.activeDescription")}
          </Text>
        </Stack>
        <Stack direction="row" gap="2" flexShrink={0}>
          <Protected minLevel={Level.Operator}>
            <AddTaskTrigger>
              <Button>
                <LuPlus />
                {t("task.add")}
              </Button>
            </AddTaskTrigger>
          </Protected>
        </Stack>
      </Stack>

      <TaskLiveStats />

      <Stack direction="row" alignItems="flex-start" justifyContent="space-between" gap="3">
        <Heading as="h2" fontSize="2xl" fontWeight="semibold">
          {t("task.taskList")}
        </Heading>
        <Stack direction="row" alignItems="center" gap="4">
          <TaskTreeModeToggle />
          <TaskFilterMenu statuses={ALL_STATUS_KEYS} />
        </Stack>
      </Stack>

      {truncated && (
        <Text textStyle="xs" color="fg.muted">
          {t("task.rowLimitNotice", { count: LIVE_ROWS_LIMIT })}
        </Text>
      )}
      <TaskTable rows={rows} isPending={isPending} />
    </Stack>
  )
}
