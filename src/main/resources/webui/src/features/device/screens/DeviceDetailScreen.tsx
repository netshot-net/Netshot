import { EmptyResult, Protected } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { LuCamera, LuSquarePen, LuChevronDown, LuRefreshCcw, LuTrash, LuZap, LuZapOff } from "react-icons/lu"
import { RouterTab, RouterTabs } from "@/components/routerTab"
import { useToast } from "@/hooks"
import { HttpStatus, isNetshotError } from "@/api/httpClient"
import { DeviceStatus, DeviceType, Level, TaskType } from "@/types"
import { TASK_TYPE_ICONS } from "@/features/task/constants"
import { Button, Flex, Group, Heading, IconButton, Menu, Portal, Skeleton, Spacer, Stack } from "@chakra-ui/react"
import { QUERIES as GLOBAL_QUERIES } from "@/constants"
import { useQueryClient } from "@tanstack/react-query"
import { useCallback, useMemo } from "react"
import { useTranslation } from "react-i18next"
import { Outlet, useParams } from "react-router"
import { useDevice, useDeviceTypes } from "../api"
import { QUERIES } from "../constants"
import { DisableDeviceTrigger, EditDeviceTrigger, EnableDeviceTrigger, RemoveDeviceTrigger } from "../components"
import { DeviceNetworkClassIcon } from "@/components/entity"
import RunDeviceScriptTrigger from "../components/RunDeviceScriptTrigger"
import DeviceSnapshotTrigger from "../components/DeviceSnapshotTrigger"
import DeviceProvider from "../contexts/DeviceProvider"

export default function DeviceDetailScreen() {
  const { id } = useParams()
  const { t } = useTranslation()
  const toast = useToast()

  const { data: device, isPending, isError, error, refetch } = useDevice(+(id ?? 0))
  const { data: deviceTypes, isPending: isDeviceTypePending } = useDeviceTypes()
  const queryClient = useQueryClient()

  const isDeviceNotFound = isError && isNetshotError(error) && error.response?.status === HttpStatus.NotFound

  const refresh = useCallback(async () => {
    const toastId = toast.loading({
      title: t("common.loading"),
      description: t("device.pleaseWaitRefreshing"),
    })
    await Promise.all([
      refetch(),
      queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_COMPLIANCE, id], refetchType: "all" }),
      queryClient.invalidateQueries({ queryKey: [GLOBAL_QUERIES.DEVICE_LIST], refetchType: "all" }),
      queryClient.invalidateQueries({ queryKey: [GLOBAL_QUERIES.DEVICE_CONFIGS, id] }),
      queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_INTERFACES, id] }),
      queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_MODULES, id] }),
      queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_DIAGNOSTIC, id] }),
      queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_TASKS, id] }),
    ])
    toast.close(toastId)
  }, [refetch, queryClient, id, toast, t])

  const isDisabled = useMemo(() => device?.status === DeviceStatus.Disabled, [device?.status])

  const deviceType = useMemo<DeviceType | undefined>(() => {
    return deviceTypes?.find((t) => t.name === device?.driver)
  }, [device?.driver, deviceTypes])

  if (isDeviceNotFound) {
    return (
      <EmptyResult title={t("device.notFound")} description={t("device.notFoundDescription")} />
    )
  }

  return (
    <DeviceProvider device={device!} type={deviceType!} isLoading={isPending || isDeviceTypePending}>
      <Stack gap="0" flex="1" overflow="auto">
        <Stack gap="5" px="9" pt="9">
          <Flex alignItems="center">
            <Skeleton loading={isPending}>
              <Stack direction="row" alignItems="center" gap="3">
                {device?.networkClass && (
                  <DeviceNetworkClassIcon networkClass={device.networkClass} size="2xl" />
                )}
                <Heading as="h1" fontSize="4xl">
                  {device?.name ?? t("common.networkDeviceTitle")}
                </Heading>
                <Tooltip content={t("common.refresh")}>
                  <IconButton
                    aria-label={t("common.refresh")}
                    variant="ghost"
                    size="sm"
                    color="fg.muted"
                    onClick={refresh}
                  >
                    <LuRefreshCcw />
                  </IconButton>
                </Tooltip>
              </Stack>
            </Skeleton>

            <Spacer />
            <Stack direction="row" gap="3">
              <Protected minLevel={Level.Operator}>
                <Skeleton loading={isPending}>
                  <DeviceSnapshotTrigger devices={[device!]}>
                    <Button variant="primary" disabled={isDisabled}>
                      <LuCamera />
                      {t("device.snapshot.take")}
                    </Button>
                  </DeviceSnapshotTrigger>
                </Skeleton>
              </Protected>

              <Protected minLevel={Level.ExecureReadWrite}>
                <Skeleton loading={isPending}>
                  <RunDeviceScriptTrigger devices={[device!]}>
                    <Button disabled={isDisabled}>
                      {TASK_TYPE_ICONS[TaskType.RunDeviceScript]}
                      {t("script.run")}
                    </Button>
                  </RunDeviceScriptTrigger>
                </Skeleton>
              </Protected>

              <Protected minLevel={Level.ReadWrite}>
                <Skeleton loading={isPending}>
                  <Menu.Root positioning={{ placement: "bottom-end" }}>
                    <Group attached>
                      <EditDeviceTrigger device={device!}>
                        <Button>
                          <LuSquarePen />
                          {t("common.edit")}
                        </Button>
                      </EditDeviceTrigger>
                      <Menu.Trigger asChild>
                        <IconButton>
                          <LuChevronDown />
                        </IconButton>
                      </Menu.Trigger>
                    </Group>

                    <Portal>
                      <Menu.Positioner>
                        <Menu.Content>
                          {device && (
                            <>
                              {isDisabled ? (
                                <EnableDeviceTrigger devices={[device]}>
                                  <Menu.Item value="enable">
                                    <LuZap />
                                    {t("common.enable")}
                                  </Menu.Item>
                                </EnableDeviceTrigger>
                              ) : (
                                <DisableDeviceTrigger devices={[device]}>
                                  <Menu.Item value="disable">
                                    <LuZapOff />
                                    {t("common.disable")}
                                  </Menu.Item>
                                </DisableDeviceTrigger>
                              )}

                              <RemoveDeviceTrigger devices={[device]}>
                                <Menu.Item value="remove" color="fg.error" _hover={{ bg: "bg.error", color: "fg.error" }}>
                                  <LuTrash />
                                  {t("common.remove")}
                                </Menu.Item>
                              </RemoveDeviceTrigger>
                            </>
                          )}
                        </Menu.Content>
                      </Menu.Positioner>
                    </Portal>
                  </Menu.Root>
                </Skeleton>
              </Protected>
            </Stack>
          </Flex>

          <RouterTabs>
            <RouterTab to="general">{t("common.general")}</RouterTab>
            <RouterTab to="configurations">{t("device.config.tabLabel")}</RouterTab>
            <RouterTab to="interfaces">{t("device.interface.list")}</RouterTab>
            <RouterTab to="modules">{t("device.module.list")}</RouterTab>
            <RouterTab to="diagnostics">{t("diagnostic.list")}</RouterTab>
            <RouterTab to="compliance">{t("compliance.label")}</RouterTab>
            <RouterTab to="tasks">{t("task.list")}</RouterTab>
          </RouterTabs>
        </Stack>
        <Stack flex="1" overflow="auto" p="9">
          <Outlet />
        </Stack>
      </Stack>
    </DeviceProvider>
  )
}
