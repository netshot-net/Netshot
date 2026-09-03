import { EmptyResult, Tooltip, VirtualizedDataTable } from "@/components"
import TaskStatusBadge from "./TaskStatusBadge"
import TaskDialog from "./TaskDialog"
import { useCustomDialog, useDialogStore } from "@/dialog"
import { DeviceBadge, DeviceGroupBadge } from "@/components/entity"
import { useLocalization } from "@/i18n"
import { LightTask, TaskType } from "@/types"
import { Icon, Skeleton, Stack, Text } from "@chakra-ui/react"
import { createColumnHelper } from "@tanstack/react-table"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import { LuCornerDownRight } from "react-icons/lu"
import { Link } from "react-router"
import { TASK_TYPE_ICONS } from "../constants"
import { useTaskTreeModeStore } from "../stores/useTaskTreeModeStore"
import { buildTaskTree, TaskTreeRow } from "../utils"

const columnHelper = createColumnHelper<TaskTreeRow<LightTask>>()

const TREE_INDENT_PX = 10

export type TaskTableProps = {
  rows: LightTask[]
  isPending: boolean
  onBottomReached?(): void
  showTarget?: boolean
  showCreator?: boolean
  showComments?: boolean
  emptyDescription?: string
  // Called right before a task row is opened, e.g. to close dialogs that should
  // no longer be visible once the clicked task's dialog takes over (see TaskChildrenDialog).
  onBeforeOpenTask?(): void
}

export default function TaskTable(props: TaskTableProps) {
  const {
    rows,
    isPending,
    onBottomReached,
    showTarget = true,
    showCreator = true,
    showComments = true,
    emptyDescription,
    onBeforeOpenTask,
  } = props
  const { t } = useTranslation()
  const { formatDateTime } = useLocalization()
  const dialog = useCustomDialog()
  const removeAllDialogs = useDialogStore((state) => state.removeAll)
  const treeMode = useTaskTreeModeStore((state) => state.treeMode)

  const displayRows = useMemo(
    () => (treeMode ? buildTaskTree(rows) : rows.map((row) => ({ ...row, depth: 0 }))),
    [rows, treeMode]
  )

  function openTask(id: number) {
    onBeforeOpenTask?.()
    dialog.open(<TaskDialog id={id} />)
  }

  const columns = useMemo(
    () => [
      columnHelper.accessor("type", {
        cell: (info) => {
          const { parentTaskId, depth } = info.row.original
          const previousRow = info.table.getRowModel().rows[info.row.index - 1]
          const showParentArrow = Boolean(parentTaskId) && previousRow?.original.id === parentTaskId

          return (
            <Stack direction="row" gap="2" alignItems="center" pl={`${depth * TREE_INDENT_PX}px`}>
              {showParentArrow && (
                <Tooltip content={t("task.childOfTask", { id: parentTaskId })}>
                  <Icon size="xs" color="grey.400" flexShrink={0}>
                    <LuCornerDownRight />
                  </Icon>
                </Tooltip>
              )}
              <Icon size="sm" flexShrink={0}>
                {TASK_TYPE_ICONS[info.getValue() as TaskType]}
              </Icon>
              <Text>{t(`task.type.${info.getValue()}`)}</Text>
            </Stack>
          )
        },
        header: t("common.type"),
        enableSorting: !treeMode,
        size: 15000,
      }),
      ...(showTarget
        ? [
            columnHelper.accessor("target", {
              cell: (info) => {
                const { deviceId, deviceGroupId, type } = info.row.original
                if (deviceId) {
                  return (
                    <DeviceBadge>
                      <Link
                        to={`/app/devices/${deviceId}/tasks`}
                        onClick={(e) => {
                          e.stopPropagation()
                          removeAllDialogs()
                        }}
                      >
                        {info.getValue()}
                      </Link>
                    </DeviceBadge>
                  )
                }
                if (deviceGroupId && type !== TaskType.PurgeDatabase) {
                  return (
                    <DeviceGroupBadge
                      id={deviceGroupId}
                      name={info.getValue()}
                      onClick={(e) => {
                        e.stopPropagation()
                        removeAllDialogs()
                      }}
                    />
                  )
                }
                return <Text>{info.getValue()}</Text>
              },
              header: t("common.target"),
              enableSorting: !treeMode,
              size: 15000,
            }),
          ]
        : []),
      ...(showCreator
        ? [
            columnHelper.accessor("author", {
              cell: (info) => <Text>{info.getValue()}</Text>,
              header: t("common.creator"),
              enableSorting: !treeMode,
              size: 8000,
            }),
          ]
        : []),
      columnHelper.accessor("status", {
        cell: (info) => <TaskStatusBadge status={info.getValue()} />,
        header: t("common.status"),
        enableSorting: !treeMode,
        size: 8000,
      }),
      columnHelper.accessor("executionDate", {
        cell: (info) => (
          <Text>{info.getValue() ? formatDateTime(info.getValue()) : t("common.nA")}</Text>
        ),
        header: t("time.executionDate"),
        enableSorting: !treeMode,
        size: 15000,
      }),
      ...(showComments
        ? [
            columnHelper.accessor("comments", {
              cell: (info) => <Text truncate>{info.getValue()}</Text>,
              header: t("common.comments"),
              enableSorting: !treeMode,
              size: 25000,
            }),
          ]
        : []),
    ],
    [t, formatDateTime, showTarget, showCreator, showComments, removeAllDialogs, treeMode]
  )

  if (isPending) {
    return (
      <Stack gap="3">
        <Skeleton h="60px" />
        <Skeleton h="60px" />
        <Skeleton h="60px" />
        <Skeleton h="60px" />
      </Stack>
    )
  }

  if (rows.length === 0) {
    return <EmptyResult title={t("task.none")} description={emptyDescription ?? t("task.noMatchingFound")} />
  }

  return (
    <VirtualizedDataTable
      columns={columns}
      data={displayRows}
      primaryKey="id"
      onClickRow={(row) => openTask(row.id)}
      onBottomReached={onBottomReached}
      maxH="540px"
    />
  )
}
