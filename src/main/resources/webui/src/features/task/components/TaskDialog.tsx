import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { LogPanel, Protected } from "@/components"
import TaskChildrenDialog from "./TaskChildrenDialog"
import TaskStatusBadge, { TASK_STATUS_CONFIG } from "./TaskStatusBadge"
import { MUTATIONS, QUERIES } from "@/constants"
import { useConfirmDialogWithMutation, useCustomDialog, useDialogConfig, useDialogStore } from "@/dialog"
import { DeviceBadge, DeviceGroupBadge } from "@/components/entity"
import { QUERIES as TASK_QUERIES, TASK_TYPE_ICONS } from "../constants"
import { useToast } from "@/hooks"
import { useLocalization } from "@/i18n"
import { Level, TaskScheduleMode, TaskScheduleType, TaskStatus, TaskType } from "@/types"
import { getSchedulePriorityLabel } from "@/utils"
import { BarSegment, BarSegmentData, useChart } from "@chakra-ui/charts"
import {
  Box,
  Button,
  CloseButton,
  ColorSwatch,
  Dialog,
  Flex,
  Heading,
  HStack,
  Icon,
  Portal,
  Separator,
  Skeleton,
  Stack,
  Text,
} from "@chakra-ui/react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useMemo } from "react"
import { LuDownload, LuFileTerminal, LuScrollText } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"

const GROUP_SCHEDULING_TYPES = [
  TaskType.TakeGroupSnapshot,
  TaskType.RunGroupDiagnostic,
  TaskType.RunDeviceGroupScript,
  TaskType.ScanSubnets,
]

// Task types that may auto-chain a single follow-up task (e.g. snapshot -> check compliance)
// once they complete, rather than pre-creating and orchestrating a known batch of children.
const CHAIN_TASK_TYPES = [
  TaskType.TakeSnapshot,
  TaskType.RunDeviceScript,
  TaskType.RunDiagnostic,
  TaskType.DiscoverDeviceType,
]

const SCHEDULE_UNIT_KEY: Partial<Record<TaskScheduleType, string>> = {
  [TaskScheduleType.Hourly]: "time.hour",
  [TaskScheduleType.Daily]: "time.day",
  [TaskScheduleType.Weekly]: "time.week",
  [TaskScheduleType.Monthly]: "time.month",
}

export type TaskDialogProps = {
  id: number
}

export default function TaskDialog(props: TaskDialogProps) {
  const { id } = props
  const { t } = useTranslation()
  const { formatDateTime } = useLocalization()
  const dialogConfig = useDialogConfig()
  const queryClient = useQueryClient()
  const toast = useToast()
  const confirmDialog = useConfirmDialogWithMutation()
  const taskDialog = useCustomDialog()
  const removeAllDialogs = useDialogStore((state) => state.removeAll)

  const { data: task, isPending } = useQuery({
    queryKey: [QUERIES.TASK, id],
    queryFn: async () => api.task.getById(id),
    enabled: Boolean(id),
    refetchIntervalInBackground: true,
    refetchInterval: (q) => {
      if (dialogConfig.props.isOpen) {
        const taskStatus = q.state.data?.status
        if (
          taskStatus === TaskStatus.Cancelled ||
          taskStatus === TaskStatus.Failure ||
          taskStatus === TaskStatus.Success
        ) {
          return false
        }
        return 5000
      }
      return false
    },
  })

  const purgeGroupId =
    task?.type === TaskType.PurgeDatabase && task?.deviceGroupId ? task.deviceGroupId : null

  const { data: purgeGroup } = useQuery({
    queryKey: [QUERIES.GROUP_DETAIL, purgeGroupId],
    queryFn: async () => api.group.getById(purgeGroupId!),
    enabled: Boolean(purgeGroupId),
  })

  const isGroupTask = Boolean(task?.type && GROUP_SCHEDULING_TYPES.includes(task.type as TaskType))
  const isChainTask = Boolean(task?.type && CHAIN_TASK_TYPES.includes(task.type as TaskType))
  const canHaveChildren = isGroupTask || isChainTask

  const { data: childSummary } = useQuery({
    queryKey: [TASK_QUERIES.TASK_SUMMARY, "children", id],
    queryFn: async () => api.task.getSummary(id),
    enabled: canHaveChildren,
    refetchIntervalInBackground: true,
    refetchInterval: (q) => {
      if (!dialogConfig.props.isOpen) return false
      const taskStatus = task?.status
      const taskOver =
        taskStatus === TaskStatus.Cancelled ||
        taskStatus === TaskStatus.Failure ||
        taskStatus === TaskStatus.Success
      if (!taskOver) return 5000
      if (isGroupTask) {
        // A group task stays RUNNING for as long as it's still scheduling/waiting on children,
        // so once it's over there is nothing left to poll for.
        return false
      }
      // A chain task (e.g. a snapshot auto-triggering a compliance check) reaches a terminal
      // status *before* spawning its follow-up task, so keep polling a bit longer, until the
      // chain itself has run its course (or we give up waiting for it to even start).
      const counts = q.state.data?.countByStatus
      const anyChildPending = counts
        ? Object.entries(counts).some(
            ([status, count]) =>
              (count ?? 0) > 0 &&
              status !== TaskStatus.Cancelled &&
              status !== TaskStatus.Failure &&
              status !== TaskStatus.Success
          )
        : false
      if (anyChildPending) return 5000
      const total = counts ? Object.values(counts).reduce((sum, count) => sum + (count ?? 0), 0) : 0
      if (total === 0) {
        // No follow-up task observed yet -- keep checking for a short grace period in case one
        // is about to be created, then stop (this task type may simply not have chained one).
        // Use changeDate (updated whenever the task record is saved, e.g. on its terminal status
        // transition) rather than executionDate (set when the task *starts* running), otherwise
        // a long-running task would already be past the grace period the moment it completes.
        const elapsedMs = task?.changeDate ? Date.now() - new Date(task.changeDate).getTime() : Infinity
        return elapsedMs < 15000 ? 3000 : false
      }
      return false
    },
  })

  // Group tasks pre-create every child upfront (including not-yet-promoted DELAYED ones), so
  // their total is stable from the very start of the run. Chain tasks instead spawn a single
  // follow-up task once they complete, and that follow-up may itself chain another one, so the
  // total grows one task at a time as the chain unfolds.
  const childTotal = childSummary
    ? Object.values(childSummary.countByStatus).reduce((sum, count) => sum + (count ?? 0), 0)
    : 0

  const childStatusData = useMemo(() => {
    if (!childSummary) return []
    return Object.entries(childSummary.countByStatus)
      .filter(([, count]) => (count ?? 0) > 0)
      .map(([status, count]) => ({
        status: status as TaskStatus,
        name: t(TASK_STATUS_CONFIG[status as TaskStatus].labelKey),
        value: count ?? 0,
        color: `${TASK_STATUS_CONFIG[status as TaskStatus].colorPalette}.solid`,
      }))
  }, [childSummary, t])

  const childChartData = useMemo<BarSegmentData[]>(
    () => childStatusData.map(({ name, value, color }) => ({ name, value, color })),
    [childStatusData]
  )

  const childChart = useChart({ data: childChartData })

  // A chain task only ever has a single follow-up task, so there is no point showing the
  // group-task breakdown chart for it -- fetch that one task instead, to link to it directly.
  const { data: chainChild } = useQuery({
    queryKey: [QUERIES.TASK, "children", id, "chain"],
    queryFn: async () => {
      const rows = await api.task.getAll({ parentTaskId: id, limit: 1 })
      return rows?.[0] ?? null
    },
    enabled: isChainTask && childTotal > 0,
    refetchIntervalInBackground: true,
    refetchInterval: (q) => {
      if (!dialogConfig.props.isOpen) return false
      const status = q.state.data?.status
      if (
        status === TaskStatus.Cancelled ||
        status === TaskStatus.Failure ||
        status === TaskStatus.Success
      ) {
        return false
      }
      return 5000
    },
  })

  function openChildren(status?: TaskStatus) {
    taskDialog.open(
      <TaskChildrenDialog
        parentTaskId={id}
        statusFilter={status}
        onBeforeOpenChild={() => dialogConfig.close()}
      />
    )
  }

  const creationDate = task?.creationDate ? formatDateTime(task?.creationDate) : null
  const priorityLabel = getSchedulePriorityLabel(task?.priority)

  // Not started yet: show the next scheduled run instead of the (empty) execution date
  const isNotYetExecuted =
    task?.status === TaskStatus.New ||
    task?.status === TaskStatus.Waiting ||
    task?.status === TaskStatus.Scheduled

  const isTaskOver =
    task?.status === TaskStatus.Success ||
    task?.status === TaskStatus.Failure ||
    task?.status === TaskStatus.Cancelled

  const executionDate = isNotYetExecuted
    ? task?.nextExecutionDate
      ? formatDateTime(task.nextExecutionDate)
      : t("task.asSoonAsPossible")
    : task?.status === TaskStatus.Cancelled
      ? t("common.cancelled")
      : task?.executionDate
        ? formatDateTime(task.executionDate)
        : null

  const scheduleLabel = task?.repeating
    ? `${t("time.every")} ${task.scheduleFactor} ${t(
        SCHEDULE_UNIT_KEY[task.scheduleType] ?? "time.day",
        { count: task.scheduleFactor }
      )}`
    : t("task.once")

  const scheduleModeLabelKey =
    task?.scheduleMode === TaskScheduleMode.Parallel
      ? "task.scheduleModeParallel"
      : task?.stopOnFailure
        ? "task.scheduleModeSequentialStop"
        : "task.scheduleModeSequentialContinue"

  const cancelMutation = useMutation({
    mutationKey: MUTATIONS.TASK_CANCEL,
    mutationFn: async () => api.task.update(id, { cancelled: true }),
    onSuccess() {
      queryClient.invalidateQueries({ queryKey: [QUERIES.TASK, id] })
      queryClient.invalidateQueries({
        predicate: (query) =>
          typeof query.queryKey[0] === "string" && query.queryKey[0].startsWith("task:"),
      })
    },
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  function openCancelConfirm() {
    const dialogRef = confirmDialog.open(MUTATIONS.TASK_CANCEL, {
      title: t("task.cancelTask"),
      description: t("task.aboutToCancelTask"),
      async onConfirm() {
        await cancelMutation.mutateAsync()
        dialogRef.close()
        toast.success({
          title: t("common.success"),
          description: t("task.taskCancelled"),
        })
      },
      confirmButton: {
        label: t("task.cancelTask"),
        props: {
          colorPalette: "red",
        },
      },
    })
  }

  return (
    <Dialog.Root
      open={dialogConfig.props.isOpen}
      placement="center"
      motionPreset="slide-in-bottom"
      size="lg"
      closeOnEscape={false}
      closeOnInteractOutside={false}
      onOpenChange={(e) => {
        if (!e.open) {
          dialogConfig.close()
        }
      }}
      onExitComplete={() => {
        dialogConfig.remove()
      }}
    >
      <Portal>
        <Dialog.Backdrop />
        <Dialog.Positioner>
          <Dialog.Content>
            <Dialog.Header
              as="h3"
              fontSize="xl"
              lineHeight="120%"
              fontWeight="bold"
              display="flex"
              alignItems="center"
              justifyContent="space-between"
            >
              {t("task.details")}
              <Stack direction="row" gap="3" alignItems="center">
                {task && (task.parentTaskId ?? 0) > 0 && (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => {
                      dialogConfig.close()
                      taskDialog.open(<TaskDialog id={task.parentTaskId!} />)
                    }}
                  >
                    {t("task.parentTask")}
                  </Button>
                )}
                <CloseButton size="sm" variant="outline" onClick={() => dialogConfig.close()} />
              </Stack>
            </Dialog.Header>
            <Dialog.Body>
              <Stack gap="6">
                <Stack gap="3">
                  <Flex alignItems="center">
                    <Box w="140px">
                      <Text color="grey.400">{t("common.type")}</Text>
                    </Box>
                    <Skeleton loading={isPending}>
                      {task?.type ? (
                        <Stack direction="row" gap="2" alignItems="center">
                          <Icon size="sm">{TASK_TYPE_ICONS[task.type as TaskType]}</Icon>
                          <Text>{t(`task.type.${task.type}`)}</Text>
                        </Stack>
                      ) : (
                        <Text>{t("common.nA")}</Text>
                      )}
                    </Skeleton>
                  </Flex>
                  <Flex alignItems="center">
                    <Box w="140px">
                      <Text color="grey.400">{t("common.description")}</Text>
                    </Box>
                    <Skeleton loading={isPending}>
                      <Text>{task?.taskDescription ?? t("common.nA")}</Text>
                    </Skeleton>
                  </Flex>
                  <Flex alignItems="center">
                    <Box w="140px">
                      <Text color="grey.400">{t("common.comments")}</Text>
                    </Box>
                    <Skeleton loading={isPending}>
                      <Text>{task?.comments ?? t("common.nA")}</Text>
                    </Skeleton>
                  </Flex>
                  <Flex alignItems="center">
                    <Box w="140px">
                      <Text color="grey.400">{t("common.target")}</Text>
                    </Box>
                    <Skeleton loading={isPending}>
                      {task?.deviceId ? (
                        <DeviceBadge>
                          <Link
                            to={`/app/devices/${task.deviceId}/tasks`}
                            onClick={() => removeAllDialogs()}
                          >
                            {task.target}
                          </Link>
                        </DeviceBadge>
                      ) : task?.deviceGroupId && task?.type !== TaskType.PurgeDatabase ? (
                        <DeviceGroupBadge
                          id={task.deviceGroupId}
                          name={task.target}
                          onClick={() => removeAllDialogs()}
                        />
                      ) : (
                        <Text>{task?.target ?? t("common.nA")}</Text>
                      )}
                    </Skeleton>
                  </Flex>
                  <Flex alignItems="center">
                    <Box w="140px">
                      <Text color="grey.400">{t("common.creation")}</Text>
                    </Box>
                    <Skeleton loading={isPending}>
                      <Text>{creationDate ?? t("common.nA")}</Text>
                    </Skeleton>
                  </Flex>
                  <Flex alignItems="center">
                    <Box w="140px">
                      <Text color="grey.400">{t("common.execution")}</Text>
                    </Box>
                    <Skeleton loading={isPending}>
                      <Text>{executionDate ?? t("common.nA")}</Text>
                    </Skeleton>
                  </Flex>
                  <Flex alignItems="center">
                    <Box w="140px">
                      <Text color="grey.400">{t("task.schedule")}</Text>
                    </Box>
                    <Skeleton loading={isPending}>
                      <Text>{scheduleLabel}</Text>
                    </Skeleton>
                  </Flex>
                  {isGroupTask && task?.scheduleMode && (
                    <Flex alignItems="center">
                      <Box w="140px">
                        <Text color="grey.400">{t("task.scheduleMode")}</Text>
                      </Box>
                      <Text>{t(scheduleModeLabelKey)}</Text>
                    </Flex>
                  )}
                  {task?.runnerId && (
                    <Flex alignItems="center">
                      <Box w="140px">
                        <Text color="grey.400">{t("task.runner")}</Text>
                      </Box>
                      <Skeleton loading={isPending}>
                        <Text>{task.runnerId}</Text>
                      </Skeleton>
                    </Flex>
                  )}
                  <Flex alignItems="center">
                    <Box w="140px">
                      <Text color="grey.400">{t("common.priority")}</Text>
                    </Box>
                    <Skeleton loading={isPending}>
                      <Text>{t(priorityLabel)}</Text>
                    </Skeleton>
                  </Flex>
                  <Flex alignItems="center">
                    <Box w="140px">
                      <Text color="grey.400">{t("common.status")}</Text>
                    </Box>
                    <Skeleton loading={isPending}>
                      {task?.status && <TaskStatusBadge status={task.status} />}
                    </Skeleton>
                  </Flex>
                </Stack>

                {task?.type === TaskType.DiscoverDeviceType && isTaskOver && (
                  <>
                    <Separator />
                    <Stack gap="3">
                      {task?.discoveredDeviceTypeDescription && (
                        <Flex alignItems="center">
                          <Box w="140px">
                            <Text color="grey.400">{t("task.discoveredDeviceType")}</Text>
                          </Box>
                          <Text>{task.discoveredDeviceTypeDescription}</Text>
                        </Flex>
                      )}
                    </Stack>
                  </>
                )}

                {task?.type === TaskType.PurgeDatabase && (
                  <>
                    <Separator />
                    <Stack gap="3">
                      <Flex alignItems="center">
                        <Box w="140px">
                          <Text color="grey.400">{t("task.purge")}</Text>
                        </Box>
                        <Text>
                          {task.days} {t("time.day", { count: task.days })}
                        </Text>
                      </Flex>
                      {(task?.configDays ?? 0) > 0 && (
                        <Flex alignItems="center">
                          <Box w="140px">
                            <Text color="grey.400">{t("device.config.purge")}</Text>
                          </Box>
                          <Text>
                            {task.configDays} {t("time.day", { count: task.configDays })}
                          </Text>
                        </Flex>
                      )}
                      {(task?.configSize ?? 0) > 0 && (
                        <Flex alignItems="center">
                          <Box w="140px">
                            <Text color="grey.400">{t("device.config.size")}</Text>
                          </Box>
                          <Text>
                            {task.configSize} {t("common.kb")}
                          </Text>
                        </Flex>
                      )}
                      {(task?.configKeepDays ?? 0) > 0 && (
                        <Flex alignItems="center">
                          <Box w="140px">
                            <Text color="grey.400">{t("device.config.keep")}</Text>
                          </Box>
                          <Text>
                            {task.configKeepDays} {t("time.day", { count: task.configKeepDays })}
                          </Text>
                        </Flex>
                      )}
                      {(task?.moduleDays ?? 0) > 0 && (
                        <Flex alignItems="center">
                          <Box w="140px">
                            <Text color="grey.400">{t("device.module.delete")}</Text>
                          </Box>
                          <Text>
                            {task.moduleDays} {t("time.day", { count: task.moduleDays })}
                          </Text>
                        </Flex>
                      )}
                      {purgeGroup && (
                        <Flex alignItems="center">
                          <Box w="140px">
                            <Text color="grey.400">{t("common.limitTo")}</Text>
                          </Box>
                          <DeviceGroupBadge
                            id={purgeGroup.id}
                            name={purgeGroup.name}
                            onClick={() => removeAllDialogs()}
                          />
                        </Flex>
                      )}
                      {task?.removeOrphanFiles && (
                        <Flex alignItems="center">
                          <Text color="grey.400">{t("device.config.removeOrphanFiles")}</Text>
                        </Flex>
                      )}
                    </Stack>
                  </>
                )}

                {task?.type === TaskType.TakeGroupSnapshot && (task?.limitToOutofdateDeviceHours ?? 0) > 0 && (
                  <>
                    <Separator />
                    <Stack gap="3">
                      <Flex alignItems="center">
                        <Box w="140px">
                          <Text color="grey.400">{t("device.limitToUnchangedFor")}</Text>
                        </Box>
                        <Text>
                          {task.limitToOutofdateDeviceHours}{" "}
                          {t("time.hour", { count: task.limitToOutofdateDeviceHours })}
                        </Text>
                      </Flex>
                    </Stack>
                  </>
                )}

                {task?.script && (
                  <>
                    <Separator />
                    <Stack gap="4">
                      <Flex alignItems="center" justifyContent="space-between">
                        <Heading size="md" fontWeight="semibold">
                          {t("script.label")}
                        </Heading>
                        <LogPanel
                          title={t("script.label")}
                          copyValue={task.script}
                          trigger={
                            <Button size="sm" variant="ghost">
                              <LuFileTerminal />
                              {t("script.view")}
                            </Button>
                          }
                        >
                          <Text fontSize="xs" whiteSpace="pre-wrap" fontFamily="mono">
                            {task.script}
                          </Text>
                        </LogPanel>
                      </Flex>
                      <Stack gap="4">
                        {task?.deviceDriver && (
                          <Flex alignItems="center">
                            <Box w="140px">
                              <Text color="grey.400">{t("admin.driver.label")}</Text>
                            </Box>
                            <Text>{task.deviceDriver}</Text>
                          </Flex>
                        )}

                        {Object.keys(task?.userInputValues ?? {}).map((key) => (
                          <Flex alignItems="center" key={key}>
                            <Box w="140px">
                              <Text color="grey.400">{key}</Text>
                            </Box>

                            <Text>{task?.userInputValues?.[key] ?? t("common.nA")}</Text>
                          </Flex>
                        ))}
                      </Stack>
                    </Stack>
                  </>
                )}

                {canHaveChildren && childTotal > 0 && (
                  <>
                    <Separator />
                    <Stack gap="3">
                      <Flex alignItems="center" justifyContent="space-between">
                        <Heading size="md" fontWeight="semibold">
                          {t("task.childTasks")}
                        </Heading>
                        {isGroupTask && (
                          <Button size="sm" variant="ghost" onClick={() => openChildren()}>
                            {t("task.viewChildTasks")}
                          </Button>
                        )}
                      </Flex>
                      {isGroupTask ? (
                        <BarSegment.Root chart={childChart} barSize="3">
                          <BarSegment.Content>
                            <BarSegment.Value />
                            <BarSegment.Bar tooltip />
                            <BarSegment.Label />
                          </BarSegment.Content>
                          <HStack wrap="wrap" gap="4" textStyle="sm">
                            {childStatusData.map((item) => (
                              <HStack
                                key={item.status}
                                gap="1.5"
                                cursor="pointer"
                                onClick={() => openChildren(item.status)}
                              >
                                <ColorSwatch value={childChart.color(item.color)} boxSize="0.82em" rounded="full" />
                                <Text>{item.name}</Text>
                                <Text fontWeight="medium">{item.value}</Text>
                                <Text color="fg.muted">
                                  {childTotal > 0 ? Math.round((item.value / childTotal) * 100) : 0}%
                                </Text>
                              </HStack>
                            ))}
                          </HStack>
                        </BarSegment.Root>
                      ) : (
                        chainChild && (
                          <Flex alignItems="center" gap="3">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => {
                                dialogConfig.close()
                                taskDialog.open(<TaskDialog id={chainChild.id} />)
                              }}
                            >
                              <Icon size="sm">{TASK_TYPE_ICONS[chainChild.type as TaskType]}</Icon>
                              {t(`task.type.${chainChild.type}`)}
                            </Button>
                            <TaskStatusBadge status={chainChild.status} />
                          </Flex>
                        )
                      )}
                    </Stack>
                  </>
                )}
              </Stack>
            </Dialog.Body>
            <Dialog.Footer justifyContent="space-between">
              <Stack direction="row" gap="2">
                {isTaskOver && (
                  <LogPanel
                    title={t("admin.logs.info")}
                    copyValue={task?.log}
                    trigger={
                      <Button size="sm" variant="ghost">
                        <LuScrollText />
                        {t("common.logs")}
                      </Button>
                    }
                  >
                    <Text fontSize="xs" whiteSpace="pre-wrap" fontFamily="mono">
                      {task?.log}
                    </Text>
                  </LogPanel>
                )}
                {task?.debugEnabled && isTaskOver && (
                  <Protected minLevel={Level.ReadWrite}>
                    <Button size="sm" variant="ghost" asChild>
                      <a
                        href={`/api/tasks/${task.id}/debuglog`}
                        download={`task-${task.id}-debug.log`}
                      >
                        <LuDownload />
                        {t("admin.logs.debug")}
                      </a>
                    </Button>
                  </Protected>
                )}
              </Stack>
              <Stack direction="row" gap="3">
                {(task?.status === TaskStatus.Scheduled ||
                  (task?.status === TaskStatus.Running && isGroupTask)) && (
                  <Protected minLevel={Level.ReadWrite}>
                    <Button
                      colorPalette="red"
                      variant="ghost"
                      onClick={openCancelConfirm}
                      loading={cancelMutation.isPending}
                    >
                      {t("task.cancelTask")}
                    </Button>
                  </Protected>
                )}
                <Button variant="default" onClick={() => dialogConfig.close()}>
                  {t("common.close")}
                </Button>
              </Stack>
            </Dialog.Footer>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  )
}
