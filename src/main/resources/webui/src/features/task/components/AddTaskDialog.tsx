import api, { CreateOrUpdateTaskPayload } from "@/api"
import { Checkbox, FormControl, Switch } from "@/components"
import { DomainSelect } from "@/features/administration/components"
import { TreeGroupSelector } from "@/features/group"
import { FormControlType } from "@/components/FormControl"
import ScheduleForm, { ScheduleFormType } from "@/components/ScheduleForm"
import TaskDialog from "./TaskDialog"
import { useCustomDialog, useDialogConfig } from "@/dialog"
import { useToast } from "@/hooks"
import { TaskType } from "@/types"
import {
  Alert,
  Button,
  CloseButton,
  Dialog,
  Heading,
  Icon,
  IconButton,
  Portal,
  RadioCard,
  Separator,
  Stack,
  Text,
} from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useEffect, useMemo, useState } from "react"
import { FormProvider, useFieldArray, useForm, useWatch } from "react-hook-form"
import { LuPlus, LuTrash } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import { TASK_TYPE_ICONS } from "../constants"

enum FormStep {
  Type,
  Details,
}

type FormData = {
  checkCompliance: boolean
  runDiagnostic: boolean
  group: number | null
  limitToOutofdateDevice: boolean
  limitToOutofdateDeviceHours?: number
  domain?: string
  subnets: string[]
  configDaysToPurge?: number
  hasConfigDaysToPurge?: boolean
  configKeepDays?: number
  hasConfigKeepDays?: boolean
  configSizeToPurge?: number
  hasConfigSizeToPurge?: boolean
  hasModuleDaysToPurge?: boolean
  moduleDaysToPurge?: number
  daysToPurge?: number
  removeOrphanFiles?: boolean
} & ScheduleFormType

export type AddTaskDialogProps = {
  initialType?: TaskType
}

export default function AddTaskDialog({ initialType }: AddTaskDialogProps = {}) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const taskDialog = useCustomDialog()
  const dialogConfig = useDialogConfig()
  const [taskType, setTaskType] = useState<TaskType | null>(initialType ?? null)
  const [formStep, setFormStep] = useState(initialType ? FormStep.Details : FormStep.Type)

  const form = useForm<FormData>({
    defaultValues: {
      subnets: [""] as string[],
      limitToOutofdateDeviceHours: 168,
      checkCompliance: true,
      runDiagnostic: true,
      group: null,
      domain: undefined,
      daysToPurge: 90,
      configDaysToPurge: 200,
      configSizeToPurge: 500,
      configKeepDays: 7,
      hasConfigDaysToPurge: false,
      hasConfigKeepDays: false,
      hasConfigSizeToPurge: false,
      hasModuleDaysToPurge: false,
      moduleDaysToPurge: 600,
      removeOrphanFiles: false,
    },
  })

  const hasConfigDaysToPurge = useWatch({
    control: form.control,
    name: "hasConfigDaysToPurge",
  })

  const hasConfigKeepDays = useWatch({
    control: form.control,
    name: "hasConfigKeepDays",
  })

  const hasConfigSizeToPurge = useWatch({
    control: form.control,
    name: "hasConfigSizeToPurge",
  })

  const hasModuleDaysToPurge = useWatch({
    control: form.control,
    name: "hasModuleDaysToPurge",
  })

  const { fields, append, remove } = useFieldArray({
    control: form.control,
    // @ts-expect-error: useFieldArray takes an array of object instead of string
    name: "subnets",
  })

  const createMutation = useMutation({
    mutationFn: api.task.create,
    onSuccess(newTask) {
      close()

      if (newTask) {
        taskDialog.open(<TaskDialog id={newTask.id} />)
      }

      queryClient.invalidateQueries({
        predicate: (query) =>
          typeof query.queryKey[0] === "string" && query.queryKey[0].startsWith("task:"),
      })
    },
    onError() {
      toast.error({
        title: t("common.error"),
        description: t("common.anErrorOccurredDuringTheTaskCreation"),
      })
    },
  })

  const hasGroup =
    taskType === TaskType.TakeGroupSnapshot ||
    taskType === TaskType.RunGroupDiagnostic ||
    taskType === TaskType.CheckGroupCompliance ||
    taskType === TaskType.CheckGroupSoftware

  const hasScheduleMode =
    taskType === TaskType.TakeGroupSnapshot || taskType === TaskType.RunGroupDiagnostic

  const submit = (values: FormData) => {
    const { schedule } = values

    const payload: CreateOrUpdateTaskPayload = {
      type: taskType ?? undefined,
      ...schedule,
    }

    if (taskType === TaskType.TakeGroupSnapshot) {
      payload.dontRunDiagnostics = !values.runDiagnostic

      if (values.limitToOutofdateDevice) {
        payload.limitToOutofdateDeviceHours = values.limitToOutofdateDeviceHours
      }
    }

    if (taskType === TaskType.TakeGroupSnapshot || taskType === TaskType.RunGroupDiagnostic) {
      payload.dontCheckCompliance = !values.checkCompliance
    }

    if (hasGroup) {
      payload.group = values.group ?? undefined
    }

    if (taskType === TaskType.ScanSubnets) {
      payload.domain = +values.domain!
      payload.subnets = values.subnets.join("\n")
    }

    if (taskType === TaskType.PurgeDatabase) {
      payload.daysToPurge = values.daysToPurge

      if (hasConfigDaysToPurge) {
        payload.configDaysToPurge = values.configDaysToPurge
      }

      payload.configSizeToPurge = hasConfigSizeToPurge ? values.configSizeToPurge : 0
      payload.configKeepDays = hasConfigKeepDays ? values.configKeepDays : 0
      payload.group = values.group ?? undefined

      if (values.hasModuleDaysToPurge) {
        payload.moduleDaysToPurge = values.moduleDaysToPurge
      }

      payload.removeOrphanFiles = values.removeOrphanFiles
    }

    createMutation.mutate(payload)
  }

  const next = () => {
    setFormStep(FormStep.Details)
    dialogConfig.update({ size: "lg" })
  }

  const previous = () => {
    setTaskType(null)
    setFormStep(FormStep.Type)
    dialogConfig.update({ size: "xl" })
  }

  const close = () => {
    dialogConfig.close()
    setTimeout(() => {
      setTaskType(null)
      setFormStep(FormStep.Type)
      dialogConfig.update({ size: "xl" })
    })
  }

  const taskTypeOptions = useMemo(
    () => [
      {
        icon: TASK_TYPE_ICONS[TaskType.TakeGroupSnapshot],
        type: TaskType.TakeGroupSnapshot,
        label: t("device.snapshot.label"),
        description: t("device.snapshot.takeOfGroup"),
      },
      {
        icon: TASK_TYPE_ICONS[TaskType.RunGroupDiagnostic],
        type: TaskType.RunGroupDiagnostic,
        label: t("diagnostic.label"),
        description: t("diagnostic.runOnGroup"),
      },
      {
        icon: TASK_TYPE_ICONS[TaskType.CheckGroupCompliance],
        type: TaskType.CheckGroupCompliance,
        label: t("device.config.compliance"),
        description: t("compliance.checkConfigOfGroup"),
      },
      {
        icon: TASK_TYPE_ICONS[TaskType.CheckGroupSoftware],
        type: TaskType.CheckGroupSoftware,
        label: t("compliance.softwareAndHardware"),
        description: t("compliance.checkSoftwareHardwareOfGroup"),
      },
      {
        icon: TASK_TYPE_ICONS[TaskType.ScanSubnets],
        type: TaskType.ScanSubnets,
        label: t("device.discover"),
        description: t("task.scanSubnetsDesc"),
      },
      {
        icon: TASK_TYPE_ICONS[TaskType.PurgeDatabase],
        type: TaskType.PurgeDatabase,
        label: t("admin.purge.database"),
        description: t("admin.purge.desc"),
      },
    ],
    [t]
  )

  const taskInfoBox = useMemo(() => {
    const selectedType = taskTypeOptions.find((opt) => opt.type === taskType)

    return (
      <Alert.Root status="info" colorPalette="green">
        <Alert.Indicator>{selectedType?.icon}</Alert.Indicator>
        <Alert.Content>
          <Alert.Title>{selectedType?.label}</Alert.Title>
          <Alert.Description>{selectedType?.description}</Alert.Description>
        </Alert.Content>
      </Alert.Root>
    )
  }, [taskType, taskTypeOptions])

  const limitToOutofdateDevice = useWatch({
    control: form.control,
    name: "limitToOutofdateDevice",
  })

  const limitToOutofdateDeviceHours = useWatch({
    control: form.control,
    name: "limitToOutofdateDeviceHours",
  })

  useEffect(() => {
    form.reset()
  }, [taskType, form])

  useEffect(() => {
    form.setValue("hasConfigKeepDays", hasConfigDaysToPurge)
    form.setValue("hasConfigSizeToPurge", hasConfigDaysToPurge)
  }, [hasConfigDaysToPurge, form])

  return (
    <FormProvider {...form}>
      <Dialog.Root
        preventScroll={false}
        placement="center"
        open={dialogConfig.props.isOpen}
        scrollBehavior="inside"
        closeOnInteractOutside={false}
        closeOnEscape={false}
        size={dialogConfig.props.size}
        onOpenChange={(e) => {
          if (!e.open) {
            close()
          }
        }}
        onExitComplete={() => {
          dialogConfig.remove()
        }}
      >
        <Portal>
          <Dialog.Backdrop />
          <Dialog.Positioner>
            <Dialog.Content as="form" onSubmit={form.handleSubmit(submit)}>
              <Dialog.Header display="flex" justifyContent="space-between" alignItems="center">
                <Heading as="h3" fontSize="2xl" fontWeight="semibold">
                  {formStep === FormStep.Type ? t("task.chooseType") : t("task.add")}
                </Heading>

                <Stack direction="row" gap="3" alignItems="center">
                  {!initialType && (
                    <Text fontSize="md" color="grey.400">
                      {t("common.stepXofY", { step: formStep === FormStep.Type ? 1 : 2, total: 2 })}
                    </Text>
                  )}
                  <CloseButton size="sm" variant="outline" onClick={close} />
                </Stack>
              </Dialog.Header>
              <Dialog.Body>
                {formStep === FormStep.Type ? (
                  <RadioCard.Root
                    value={taskType}
                    onValueChange={({ value }) => setTaskType(value as TaskType)}
                    display="grid"
                    gridTemplateColumns="repeat(3, 1fr)"
                    width="full"
                    gap="5"
                    size="lg"
                  >
                    {taskTypeOptions.map((option) => (
                      <RadioCard.Item key={option.type} value={option.type}>
                        <RadioCard.ItemHiddenInput />
                        <RadioCard.ItemControl>
                          <RadioCard.ItemContent>
                            <Icon size="xl" mb="2">
                              {option.icon}
                            </Icon>
                            <RadioCard.ItemText>{option.label}</RadioCard.ItemText>
                            <RadioCard.ItemDescription>
                              {option.description}
                            </RadioCard.ItemDescription>
                          </RadioCard.ItemContent>
                          <RadioCard.ItemIndicator />
                        </RadioCard.ItemControl>
                      </RadioCard.Item>
                    ))}
                  </RadioCard.Root>
                ) : (
                  <Stack gap="6">
                    {taskInfoBox}

                    {hasGroup && (
                      <TreeGroupSelector
                        label={t("group.toProcess")}
                        control={form.control}
                        name="group"
                        required
                        shouldUnregister
                      />
                    )}

                    {taskType === TaskType.TakeGroupSnapshot && (
                      <>
                        <Stack direction="row" alignItems="center" gap="3">
                          <Checkbox control={form.control} name="limitToOutofdateDevice">
                            {t("device.limitToUnchangedFor")}
                          </Checkbox>

                          <FormControl
                            control={form.control}
                            name="limitToOutofdateDeviceHours"
                            type={FormControlType.Number}
                            disabled={!limitToOutofdateDevice}
                            required={limitToOutofdateDevice}
                            min={1}
                            w="44"
                            suffix={
                              <Text color="grey.400">
                                {t("time.hour", { count: Number(limitToOutofdateDeviceHours) })}
                              </Text>
                            }
                          />
                        </Stack>
                        <Checkbox control={form.control} name="runDiagnostic">
                          {t("device.runDiagnosticsAfterSnapshot")}
                        </Checkbox>
                      </>
                    )}

                    {taskType != null && [TaskType.TakeGroupSnapshot, TaskType.RunGroupDiagnostic].includes(
                      taskType
                    ) && (
                      <Checkbox control={form.control} name="checkCompliance">
                        {t("device.checkComplianceAfterSnapshot")}
                      </Checkbox>
                    )}

                    {taskType === TaskType.ScanSubnets && (
                      <>
                        <DomainSelect required control={form.control} name="domain" />
                        <Stack gap="6">
                          <Heading as="h5" size="sm">
                            {t("task.subnetsOrIpAddresses")}
                          </Heading>
                          {fields.length > 0 && (
                            <Stack gap="3">
                              {fields.map((field, index) => (
                                <Stack direction="row" gap="4" key={field.id}>
                                  <FormControl
                                    required
                                    control={form.control}
                                    name={`subnets.${index}`}
                                    placeholder={t("network.enterIpAddress")}
                                    rules={{
                                      pattern: {
                                        value:
                                          /(?:(?:25[0-5]|2[0-4]\d|[01]?\d?\d{1})\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d?\d{1})/g,
                                        message: t("common.thisIsNotAValidIpAddress"),
                                      },
                                    }}
                                  />
                                  {fields.length > 1 && (
                                    <IconButton
                                      onClick={() => remove(index)}
                                      variant="ghost"
                                      colorPalette="green"
                                      aria-label={t("network.removeSubnet")}
                                    >
                                      <LuTrash />
                                    </IconButton>
                                  )}
                                </Stack>
                              ))}
                            </Stack>
                          )}
                          <Stack direction="row">
                            <Button onClick={() => append("")}>
                              <LuPlus />
                              {t("common.addEntry")}
                            </Button>
                          </Stack>
                        </Stack>
                      </>
                    )}

                    {taskType === TaskType.PurgeDatabase && (
                      <Stack gap="5">
                        <Stack gap="3">
                          <Stack gap="0">
                            <Text fontWeight="medium">{t("task.purge")}</Text>
                            <Text color="grey.400">{t("task.purgeFinishedMoreThan")}</Text>
                          </Stack>
                          <FormControl
                            control={form.control}
                            name="daysToPurge"
                            type={FormControlType.Number}
                            suffix={
                              <Text color="grey.400" pr="5">
                                {t("time.days")}
                              </Text>
                            }
                          />
                        </Stack>
                        <Separator />
                        <Stack gap="3">
                          <Stack direction="row" gap="6">
                            <Stack gap="0" flex="1">
                              <Text fontWeight="medium">{t("device.config.purge")}</Text>
                              <Text color="grey.400">
                                {t("device.config.deleteOldPriorTo")}
                              </Text>
                            </Stack>
                            <Switch
                              w="initial"
                              control={form.control}
                              name="hasConfigDaysToPurge"
                            />
                          </Stack>
                          <FormControl
                            disabled={!hasConfigDaysToPurge}
                            control={form.control}
                            name="configDaysToPurge"
                            type={FormControlType.Number}
                            suffix={
                              <Text color="grey.400" pr="5">
                                {t("time.days")}
                              </Text>
                            }
                          />
                        </Stack>
                        <Stack gap="3">
                          <Stack direction="row" gap="6">
                            <Stack gap="0" flex="1">
                              <Text fontWeight="medium">{t("device.config.size")}</Text>
                              <Text color="grey.400">{t("device.config.onlyBiggerThan")}</Text>
                            </Stack>
                            <Switch
                              w="initial"
                              control={form.control}
                              name="hasConfigSizeToPurge"
                            />
                          </Stack>
                          <FormControl
                            disabled={!hasConfigSizeToPurge}
                            control={form.control}
                            name="configSizeToPurge"
                            type={FormControlType.Number}
                            suffix={
                              <Text color="grey.400" pr="5">
                                {t("common.kb")}
                              </Text>
                            }
                          />
                        </Stack>
                        <Stack gap="3">
                          <Stack direction="row" gap="6">
                            <Stack gap="0" flex="1">
                              <Text fontWeight="medium">{t("device.config.keep")}</Text>
                              <Text color="grey.400">
                                {t("device.config.keepOneEvery")}
                              </Text>
                            </Stack>
                            <Switch w="initial" control={form.control} name="hasConfigKeepDays" />
                          </Stack>
                          <FormControl
                            disabled={!hasConfigKeepDays}
                            control={form.control}
                            name="configKeepDays"
                            type={FormControlType.Number}
                            suffix={
                              <Text color="grey.400" pr="5">
                                {t("time.days")}
                              </Text>
                            }
                          />
                        </Stack>
                        <Stack gap="3">
                          <Stack direction="row" gap="6">
                            <Stack gap="0" flex="1">
                              <Text fontWeight="medium">{t("device.module.delete")}</Text>
                              <Text color="grey.400">{t("device.module.deleteRemovedMoreThan")}</Text>
                            </Stack>
                            <Switch
                              w="initial"
                              control={form.control}
                              name="hasModuleDaysToPurge"
                            />
                          </Stack>
                          <FormControl
                            disabled={!hasModuleDaysToPurge}
                            control={form.control}
                            name="moduleDaysToPurge"
                            type={FormControlType.Number}
                            suffix={
                              <Text color="grey.400" pr="5">
                                {t("time.daysAgo")}
                              </Text>
                            }
                          />
                        </Stack>
                        <Checkbox control={form.control} name="removeOrphanFiles">
                          {t("device.config.removeOrphanFiles")}
                        </Checkbox>
                        <Separator />
                        <TreeGroupSelector
                          label={t("common.limitTo")}
                          control={form.control}
                          name="group"
                          shouldUnregister
                        />
                        <Separator />
                      </Stack>
                    )}

                    <ScheduleForm showScheduleMode={hasScheduleMode} />
                  </Stack>
                )}
              </Dialog.Body>
              <Dialog.Footer justifyContent="flex-end">
                <Stack direction="row" gap="3" alignItems="center">
                  <Button onClick={close}>{t("common.cancel")}</Button>
                  <Button onClick={previous} disabled={!!initialType || formStep === FormStep.Type}>
                    {t("common.previous")}
                  </Button>
                  {formStep === FormStep.Type && (
                    <Button disabled={taskType === null} onClick={next}>
                      {t("common.next")}
                    </Button>
                  )}

                  {formStep === FormStep.Details && (
                    <Button
                      type="submit"
                      disabled={!form.formState.isValid}
                      loading={createMutation.isPending}
                      variant="primary"
                    >
                      {t("common.create")}
                    </Button>
                  )}
                </Stack>
              </Dialog.Footer>
            </Dialog.Content>
          </Dialog.Positioner>
        </Portal>
      </Dialog.Root>
    </FormProvider>
  )
}
