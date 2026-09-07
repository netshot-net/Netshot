import api, { CreateOrUpdateTaskPayload } from "@/api"
import { NetshotError } from "@/api/httpClient"
import { DomainSelect } from "@/features/administration/components"
import FormControl from "@/components/FormControl"
import { LuPlus, LuTrash } from "react-icons/lu"
import ScheduleForm, { ScheduleFormType } from "@/components/ScheduleForm"
import { TaskDialog } from "@/features/task/components"
import { MUTATIONS } from "@/constants"
import { useCustomDialog, useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { TaskType } from "@/types"
import { Button, Heading, IconButton, Stack } from "@chakra-ui/react"
import { useMutation } from "@tanstack/react-query"
import { useEffect } from "react"
import { useFieldArray, useForm, useFormContext } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"

type Form = { domainId: string | null; subnets: string[] } & ScheduleFormType

export type DeviceScanSubnetTriggerProps = { children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

function DeviceCreateForm() {
  const form = useFormContext()
  const { t } = useTranslation()
  const { fields, append, remove } = useFieldArray({ control: form.control, name: "subnets" })

  useEffect(() => { append("") }, [append])

  return (
    <Stack gap="6">
      <DomainSelect required control={form.control} name="domainId" />
      <Stack gap="6">
        <Heading as="h5" size="sm">{t("task.subnetsOrIpAddresses")}</Heading>
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
                      value: /(?:(?:25[0-5]|2[0-4]\d|[01]?\d?\d{1})\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d?\d{1})/g,
                      message: t("common.thisIsNotAValidIpAddress"),
                    },
                  }}
                />
                {fields.length > 1 && (
                  <IconButton onClick={() => remove(index)} variant="ghost" colorPalette="green" aria-label={t("network.removeSubnet")}>
                    <LuTrash />
                  </IconButton>
                )}
              </Stack>
            ))}
          </Stack>
        )}
        <Stack direction="row">
          <Button onClick={() => append("")}><LuPlus />{t("common.addEntry")}</Button>
        </Stack>
      </Stack>
      <ScheduleForm />
    </Stack>
  )
}

export default function DeviceScanSubnetTrigger({ children, ...rest }: DeviceScanSubnetTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const dialog = useFormDialogWithMutation()
  const taskDialog = useCustomDialog()

  const form = useForm<Form>({
    mode: "onChange",
    reValidateMode: "onChange",
    defaultValues: { domainId: null, subnets: [] },
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.TASK_CREATE,
    mutationFn: async (payload: CreateOrUpdateTaskPayload) => api.task.create(payload),
    onError(err: NetshotError) { toast.error(err) },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.TASK_CREATE, {
      title: t("task.scanSubnets"),
      description: <DeviceCreateForm />,
      form,
      async onSubmit(values: Form) {
        const { schedule } = values
        const task = await mutation.mutateAsync({
          type: TaskType.ScanSubnets,
          subnets: values.subnets.join("\n"),
          domain: +(values.domainId ?? 0),
          ...schedule,
        })
        await dialogRef.close()
        taskDialog.open(<TaskDialog id={task!.id} />)
      },
      size: "lg",
      submitButton: { label: t("common.run") },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
