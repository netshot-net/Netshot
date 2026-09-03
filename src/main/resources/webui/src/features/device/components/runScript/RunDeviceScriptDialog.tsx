import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import {
  Checkbox,
  MonacoEditorControl,
  ScheduleForm,
} from "@/components"
import DeviceTypeSelect from "../DeviceTypeSelect"
import { ScheduleFormType } from "@/components/ScheduleForm"
import { TaskDialog } from "@/features/task/components"
import { MUTATIONS } from "@/constants"
import { useCustomDialog, useDialogConfig } from "@/dialog"
import { useDeviceTypeOptions } from "@/features/device/hooks"
import { useToast } from "@/hooks"
import { Device, DeviceTypeProtocol, DriverOptionType, Script, ScriptUserInputDefinition, LightDevice, TaskType } from "@/types"
import DriverValueField from "@/features/device/components/DriverValueField"
import {
  Badge,
  Box,
  Button,
  CloseButton,
  Dialog,
  Flex,
  Heading,
  Icon,
  Portal,
  Separator,
  Stack,
  Text,
} from "@chakra-ui/react"
import { useMutation } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import { FormProvider, useForm, useFormContext, useWatch } from "react-hook-form"
import { LuFileTerminal, LuMinimize2, LuPencil, LuPlus, LuSave } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import { DeviceNamesPreview } from "@/features/device/components"
import { NEW_HTTP_SCRIPT_TEMPLATE, NEW_SCRIPT_TEMPLATE } from "./constants"
import LoadScriptButton from "./LoadScriptButton"
import SaveScriptDialog from "./SaveScriptDialog"

enum FormStep {
  Configure,
  Run,
}

type ScriptMeta = {
  id: number
  name: string
  folder: string
}

type RunDeviceScriptForm = {
  driver: string | null
  script: string
  userInputs: Record<string, string | boolean>
  debugEnabled: boolean
  runSnapshot: boolean
  runDiagnostics: boolean
  checkCompliance: boolean
} & ScheduleFormType

type ConfigureStepProps = {
  isExpanded: boolean
  scriptMeta: ScriptMeta | null
  isNewScript: boolean
  isDirty: boolean
  onLoad(script: Script): void
  onWriteNew(): void
  onEdit(): void
  onSave(): void
}

function ConfigureStep(props: ConfigureStepProps) {
  const { isExpanded, scriptMeta, isNewScript, isDirty, onLoad, onWriteNew, onEdit, onSave } = props
  const { t } = useTranslation()
  const form = useFormContext<RunDeviceScriptForm>()

  const leftColumn = (
    <Stack
      gap="6"
      w={isExpanded ? "280px" : "full"}
      flexShrink={0}
      h={isExpanded ? "full" : undefined}
      p="1"
    >
      <DeviceTypeSelect
        required
        control={form.control}
        name="driver"
        label={t("admin.driver.label")}
      />
      {scriptMeta ? (
        <Stack gap="3">
          <Stack gap="1">
            <Text fontSize="sm" color="grey.400">
              {t("script.label")}
            </Text>
            <Flex alignItems="center" gap="3" p="3" borderRadius="md" borderWidth="1px">
              <Icon color="green.600" size="md">
                <LuFileTerminal />
              </Icon>
              <Text fontWeight="semibold" flex="1" lineClamp={1}>
                {scriptMeta.name}
              </Text>
              {isDirty && (
                <Badge size="sm" colorPalette="orange" variant="subtle">
                  {t("script.modified")}
                </Badge>
              )}
            </Flex>
          </Stack>
          <Stack direction="row" gap="2" flexWrap="wrap">
            <LoadScriptButton size="sm" label={t("script.load")} onLoad={onLoad} />
            {!isExpanded && (
              <Button size="sm" onClick={onEdit}>
                <LuPencil />
                {t("common.edit")}
              </Button>
            )}
            {(isExpanded || isDirty) && (
              <Button size="sm" onClick={onSave}>
                <LuSave />
                {t("common.save")}
              </Button>
            )}
          </Stack>
        </Stack>
      ) : isNewScript ? (
        <Stack gap="3">
          <Text fontSize="sm" color="grey.400">
            {t("script.newScript")}
          </Text>
          <Stack direction="row" gap="2" flexWrap="wrap">
            {!isExpanded && (
              <Button size="sm" onClick={onEdit}>
                <LuPencil />
                {t("common.edit")}
              </Button>
            )}
            <Button size="sm" onClick={onSave}>
              <LuSave />
              {t("common.save")}
            </Button>
          </Stack>
        </Stack>
      ) : (
        <Stack gap="3">
          <LoadScriptButton onLoad={onLoad} />
          <Stack direction="row" alignItems="center" gap="3">
            <Separator flex="1" />
            <Text color="grey.400" fontSize="sm">
              {t("common.or")}
            </Text>
            <Separator flex="1" />
          </Stack>
          <Button onClick={onWriteNew}>
            <LuPlus />
            {t("script.writeNew")}
          </Button>
        </Stack>
      )}
    </Stack>
  )

  if (!isExpanded) {
    return (
      <Stack gap="6" flex="1">
        {leftColumn}
      </Stack>
    )
  }

  return (
    <Stack direction="row" gap="7" overflow="auto" flex="1">
      {leftColumn}
      <Stack flex="1" overflow="auto">
        <MonacoEditorControl required control={form.control} name="script" language="typescript" scriptKind="runScript" />
      </Stack>
    </Stack>
  )
}

type RunStepProps = {
  devices: LightDevice[] | Device[]
  onReorderDevices(devices: LightDevice[] | Device[]): void
  inputs: ScriptUserInputDefinition[]
}

function RunStep(props: RunStepProps) {
  const { devices, onReorderDevices, inputs } = props
  const { t } = useTranslation()
  const form = useFormContext<RunDeviceScriptForm>()

  return (
    <Stack gap="6" flex="1">
      <Stack gap="3">
        {devices.length > 1 ? (
          <Flex>
            <Box w="140px">
              <Text color="grey.400">{t("device.devices")}</Text>
            </Box>
            <DeviceNamesPreview devices={devices} onReorder={onReorderDevices} />
          </Flex>
        ) : (
          <>
            <Flex alignItems="center">
              <Box w="140px">
                <Text color="grey.400">{t("common.name")}</Text>
              </Box>
              <Text>{devices?.[0]?.name ?? "nA"}</Text>
            </Flex>
            <Flex alignItems="center">
              <Box w="140px">
                <Text color="grey.400">{t("device.interface.ipAddress")}</Text>
              </Box>
              <Text>{devices?.[0]?.mgmtAddress ?? "nA"}</Text>
            </Flex>
          </>
        )}
      </Stack>
      {inputs.length > 0 && (
        <>
          <Separator />
          <Stack gap="4">
            <Heading as="h5" size="md">
              {t("script.parameters")}
            </Heading>
            {inputs.map((input) => (
              <DriverValueField
                key={input.name}
                control={form.control}
                name={`userInputs.${input.name}`}
                definition={{
                  type: input.type,
                  label: input.label,
                  description: input.description,
                  choices: input.choices,
                }}
              />
            ))}
          </Stack>
          <Separator />
        </>
      )}
      <Stack gap="3">
        <Checkbox control={form.control} name="runSnapshot">
          {t("device.runSnapshotAfterScript")}
        </Checkbox>
        <Checkbox control={form.control} name="runDiagnostics">
          {t("device.runDiagnosticsAfterScript")}
        </Checkbox>
        <Checkbox control={form.control} name="checkCompliance">
          {t("device.checkComplianceAfterScript")}
        </Checkbox>
        <Checkbox control={form.control} name="debugEnabled">
          {t("device.enableFullTrace")}
        </Checkbox>
      </Stack>
      <ScheduleForm showScheduleMode={devices.length > 1} />
    </Stack>
  )
}

export type RunDeviceScriptDialogProps = {
  devices: LightDevice[] | Device[]
}

export default function RunDeviceScriptDialog(props: RunDeviceScriptDialogProps) {
  const { devices } = props
  const { t } = useTranslation()
  const toast = useToast()
  const dialogConfig = useDialogConfig()
  const customDialog = useCustomDialog()

  const [formStep, setFormStep] = useState(FormStep.Configure)
  const [isExpanded, setIsExpanded] = useState(false)
  const [scriptMeta, setScriptMeta] = useState<ScriptMeta | null>(null)
  const [isNewScript, setIsNewScript] = useState(false)
  const [originalContent, setOriginalContent] = useState("")
  const [orderedDevices, setOrderedDevices] = useState(devices)

  const form = useForm<RunDeviceScriptForm>({
    mode: "onChange",
    defaultValues: {
      driver: devices?.[0]?.driver ?? null,
      script: "",
      userInputs: {},
      debugEnabled: false,
      runSnapshot: true,
      runDiagnostics: true,
      checkCompliance: true,
    },
  })

  const { getOptionByDriver } = useDeviceTypeOptions()

  const scriptValue = useWatch({ control: form.control, name: "script" })
  const driverValue = useWatch({ control: form.control, name: "driver" })
  const isDirty = scriptMeta != null && scriptValue !== originalContent
  const canProceedToRun = Boolean(driverValue) && Boolean(scriptValue?.trim())

  function applyConfigureSize(expanded: boolean) {
    dialogConfig.update({
      variant: expanded ? "full-floating" : undefined,
      size: expanded ? "lg" : "md",
    })
  }

  function expand() {
    setIsExpanded(true)
    applyConfigureSize(true)
  }

  function collapse() {
    setIsExpanded(false)
    applyConfigureSize(false)
  }

  function loadScript(script: Script) {
    form.setValue("script", script.script, { shouldValidate: true })
    form.setValue("driver", script.deviceDriver, { shouldValidate: true })
    setScriptMeta({ id: script.id, name: script.name, folder: script.folder })
    setIsNewScript(false)
    setOriginalContent(script.script)
  }

  function writeNewScript() {
    const protocols = getOptionByDriver(driverValue)?.value?.protocols ?? []
    const hasCliAccess = protocols.includes(DeviceTypeProtocol.Ssh) || protocols.includes(DeviceTypeProtocol.Telnet)
    const hasHttpAccess = protocols.includes(DeviceTypeProtocol.Http) || protocols.includes(DeviceTypeProtocol.Https)
    const template = !hasCliAccess && hasHttpAccess ? NEW_HTTP_SCRIPT_TEMPLATE : NEW_SCRIPT_TEMPLATE

    form.setValue("script", template, { shouldValidate: true })
    setScriptMeta(null)
    setIsNewScript(true)
    setOriginalContent(template)
    expand()
  }

  function openSaveDialog() {
    const values = form.getValues()

    customDialog.open(
      <SaveScriptDialog
        initialName={scriptMeta?.name ?? ""}
        initialFolder={scriptMeta?.folder ?? ""}
        driver={values.driver!}
        scriptContent={values.script}
        onSaved={(saved) => {
          setScriptMeta({ id: saved.id, name: saved.name, folder: saved.folder })
          setIsNewScript(false)
          setOriginalContent(values.script)
          toast.success({
            title: t("common.success"),
            description: t("script.savedForLaterUse"),
          })
        }}
      />,
      { size: "lg" }
    )
  }

  function previous() {
    setFormStep(FormStep.Configure)
    applyConfigureSize(isExpanded)
  }

  function close() {
    dialogConfig.close()
  }

  const [validatedScript, setValidatedScript] = useState<Script | null>(null)

  const validateMutation = useMutation({
    mutationKey: MUTATIONS.SCRIPT_VALIDATE,
    mutationFn: (values: { driver: string; script: string }) =>
      api.script.validate({
        deviceDriver: values.driver,
        name: "#",
        script: values.script,
      }),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  async function next() {
    if (!driverValue || !scriptValue) return

    let script: Script | null
    try {
      script = await validateMutation.mutateAsync({ driver: driverValue, script: scriptValue })
    } catch {
      return
    }
    if (!script) return

    const userInputs: Record<string, string | boolean> = {}
    for (const key in script.userInputDefinitions) {
      const definition = script.userInputDefinitions[key]
      userInputs[definition.name] =
        definition.type === DriverOptionType.Boolean
          ? definition.defaultValue === "true"
          : (definition.defaultValue ?? "")
    }
    form.setValue("userInputs", userInputs)
    setValidatedScript(script)
    setFormStep(FormStep.Run)
    dialogConfig.update({ variant: undefined, size: "lg" })
  }

  const inputs = useMemo(() => {
    if (!validatedScript?.userInputDefinitions) return []
    return Object.keys(validatedScript.userInputDefinitions).map(
      (key) => validatedScript.userInputDefinitions[key]
    )
  }, [validatedScript])

  const runMutation = useMutation({
    mutationKey: MUTATIONS.TASK_CREATE,
    mutationFn: api.task.create,
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  async function submit(values: RunDeviceScriptForm) {
    const {
      schedule,
      userInputs: rawUserInputs,
      debugEnabled,
      runSnapshot,
      runDiagnostics,
      checkCompliance,
      driver,
      script,
    } = values

    // Wire format is Map<String,String> - booleans (from Switch fields) are
    // sent as the literal "true"/"false" strings, matching driver-loader.js's
    // validateUserInputs().
    const userInputs: Record<string, string> = {}
    for (const key in rawUserInputs) {
      const value = rawUserInputs[key]
      userInputs[key] = typeof value === "boolean" ? String(value) : value
    }

    const task = await runMutation.mutateAsync(
      devices.length > 1
        ? {
            type: TaskType.RunDeviceGroupScript,
            deviceList: orderedDevices.map((device) => device.id),
            driver: driver!,
            script,
            userInputs,
            runSnapshot,
            runDiagnostics,
            checkCompliance,
            ...schedule,
          }
        : {
            type: TaskType.RunDeviceScript,
            device: devices?.[0]?.id,
            driver: driver!,
            script,
            userInputs,
            debugEnabled,
            runSnapshot,
            runDiagnostics,
            checkCompliance,
            ...schedule,
          }
    )

    close()

    if (task) {
      customDialog.open(<TaskDialog id={task.id} />)
    }
  }

  const title = t("script.run")

  return (
    <FormProvider {...form}>
      <Dialog.Root
        open={dialogConfig.props.isOpen}
        placement="center"
        motionPreset="slide-in-bottom"
        size={dialogConfig.props.size}
        variant={dialogConfig.props.variant}
        closeOnInteractOutside={false}
        closeOnEscape={false}
        scrollBehavior="inside"
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
                  {title}
                </Heading>

                <Stack direction="row" gap="3" alignItems="center">
                  {formStep === FormStep.Configure && isExpanded && (
                    <Button size="sm" variant="ghost" onClick={collapse}>
                      <LuMinimize2 />
                      {t("script.exitEditMode")}
                    </Button>
                  )}
                  <Text fontSize="md" color="grey.400">
                    {t("common.stepXofY", { step: formStep === FormStep.Configure ? 1 : 2, total: 2 })}
                  </Text>
                  <CloseButton size="sm" variant="outline" onClick={close} />
                </Stack>
              </Dialog.Header>
              <Dialog.Body flex="1" display="flex" overflow={formStep === FormStep.Run ? "auto" : undefined}>
                {formStep === FormStep.Configure ? (
                  <ConfigureStep
                    isExpanded={isExpanded}
                    scriptMeta={scriptMeta}
                    isNewScript={isNewScript}
                    isDirty={isDirty}
                    onLoad={loadScript}
                    onWriteNew={writeNewScript}
                    onEdit={expand}
                    onSave={openSaveDialog}
                  />
                ) : (
                  <RunStep
                    devices={orderedDevices}
                    onReorderDevices={setOrderedDevices}
                    inputs={inputs}
                  />
                )}
              </Dialog.Body>
              <Dialog.Footer justifyContent="flex-end">
                <Stack direction="row" gap="3" alignItems="center">
                  <Button onClick={close}>{t("common.cancel")}</Button>
                  <Button onClick={previous} disabled={formStep === FormStep.Configure}>
                    {t("common.previous")}
                  </Button>
                  {formStep === FormStep.Configure && (
                    <Button
                      disabled={!canProceedToRun || validateMutation.isPending}
                      loading={validateMutation.isPending}
                      onClick={next}
                    >
                      {t("common.next")}
                    </Button>
                  )}
                  {formStep === FormStep.Run && (
                    <Button
                      type="submit"
                      variant="primary"
                      loading={runMutation.isPending}
                      disabled={!form.formState.isValid}
                    >
                      {t("common.run")}
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
