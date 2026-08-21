import api from "@/api"
import { TASK_STATUS_CONFIG } from "./TaskStatusBadge"
import { Tooltip } from "@/components/ui/tooltip"
import { QUERIES } from "@/constants"
import { useDialogConfig } from "@/dialog"
import TaskTable from "./TaskTable"
import { TaskStatus } from "@/types"
import { Badge, Button, CloseButton, Dialog, Icon, IconButton, Portal, Stack } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { useState } from "react"
import { LuRefreshCcw, LuX } from "react-icons/lu"
import { useTranslation } from "react-i18next"

export type TaskChildrenDialogProps = {
  parentTaskId: number
  statusFilter?: TaskStatus
  // Called right before a child task's dialog is opened, so the caller can close
  // dialogs upstream of this one (e.g. the parent task's own dialog).
  onBeforeOpenChild?: () => void
}

export default function TaskChildrenDialog(props: TaskChildrenDialogProps) {
  const { parentTaskId, statusFilter: initialStatusFilter, onBeforeOpenChild } = props
  const { t } = useTranslation()
  const dialogConfig = useDialogConfig()
  const [statusFilter, setStatusFilter] = useState(initialStatusFilter)

  const {
    data: children,
    isPending,
    isFetching,
    refetch,
  } = useQuery({
    queryKey: [QUERIES.TASK, "children", parentTaskId, statusFilter],
    queryFn: async () => api.task.getAll({ parentTaskId, status: statusFilter, limit: 500 }),
  })

  return (
    <Dialog.Root
      open={dialogConfig.props.isOpen}
      placement="center"
      motionPreset="slide-in-bottom"
      size="xl"
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
            <Dialog.Header as="h3" fontSize="xl" lineHeight="120%" fontWeight="bold">
              <Stack direction="row" alignItems="center" gap="3">
                <span>{t("task.childTasks")}</span>
                <Tooltip content={t("common.refresh")}>
                  <IconButton
                    aria-label={t("common.refresh")}
                    variant="ghost"
                    size="sm"
                    color="fg.muted"
                    onClick={() => refetch()}
                    loading={isFetching}
                  >
                    <LuRefreshCcw />
                  </IconButton>
                </Tooltip>
                {statusFilter && (
                  <Badge
                    variant="surface"
                    colorPalette={TASK_STATUS_CONFIG[statusFilter].colorPalette}
                    display="inline-flex"
                    alignItems="center"
                    gap="1"
                    cursor="pointer"
                    fontWeight="normal"
                    onClick={() => setStatusFilter(undefined)}
                  >
                    <Icon size="sm">{TASK_STATUS_CONFIG[statusFilter].icon}</Icon>
                    {t(TASK_STATUS_CONFIG[statusFilter].labelKey)}
                    <Icon size="xs">
                      <LuX />
                    </Icon>
                  </Badge>
                )}
              </Stack>
            </Dialog.Header>
            <Dialog.Body>
              <TaskTable
                rows={children ?? []}
                isPending={isPending}
                showTarget
                showCreator={false}
                showComments={false}
                onBeforeOpenTask={() => {
                  dialogConfig.close()
                  onBeforeOpenChild?.()
                }}
              />
            </Dialog.Body>
            <Dialog.Footer>
              <Button variant="outline" onClick={() => dialogConfig.close()}>
                {t("common.close")}
              </Button>
            </Dialog.Footer>
            <Dialog.CloseTrigger asChild>
              <CloseButton size="sm" variant="outline" />
            </Dialog.CloseTrigger>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  )
}
