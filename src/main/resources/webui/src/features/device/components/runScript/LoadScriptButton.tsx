import api from "@/api"
import { useTreeOpenKeys } from "@/hooks"
import { TreeScript } from "../scriptTree"
import { QUERIES } from "@/constants"
import { useFormDialog } from "@/dialog"
import { Script, ScriptBrief } from "@/types"
import { createFoldersFromScripts } from "@/utils"
import { Box, Button, ButtonProps, Center, Spinner, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { useCallback, useMemo } from "react"
import { useController, useForm, useFormContext } from "react-hook-form"
import { LuFolderOpen } from "react-icons/lu"
import { useTranslation } from "react-i18next"

type SelectScriptForm = {
  scriptId: number | null
}

function SelectScriptDialogBody() {
  const form = useFormContext<SelectScriptForm>()
  const { t } = useTranslation()

  const { data: scripts, isPending } = useQuery({
    queryKey: [QUERIES.SCRIPT_LIST],
    queryFn: async () => api.script.getAll(),
  })

  const { field } = useController({
    control: form.control,
    name: "scriptId",
    rules: { required: true },
  })
  const scriptId = field.value

  const items = useMemo(() => createFoldersFromScripts(scripts ?? []), [scripts])
  const { isOpen, toggle } = useTreeOpenKeys()

  const isSelected = useCallback((script: ScriptBrief) => script.id === scriptId, [scriptId])

  const onScriptSelect = useCallback(
    (script: ScriptBrief) => {
      field.onChange(script.id)
    },
    [field]
  )

  if (isPending) {
    return (
      <Center>
        <Spinner />
      </Center>
    )
  }

  if (!scripts?.length) {
    return (
      <Center py="8">
        <Text color="fg.muted">{t("script.noScriptFound")}</Text>
      </Center>
    )
  }

  return (
    <Box py="2">
      <TreeScript
        items={items}
        onScriptSelect={onScriptSelect}
        isSelected={isSelected}
        isFolderOpen={isOpen}
        toggleFolderOpen={toggle}
        showMenu
      />
    </Box>
  )
}

export type LoadScriptButtonProps = {
  onLoad(script: Script): void
  label?: string
} & Omit<ButtonProps, "onLoad">

export default function LoadScriptButton(props: LoadScriptButtonProps) {
  const { onLoad, label, ...other } = props
  const { t } = useTranslation()
  const dialog = useFormDialog()

  const dialogForm = useForm<SelectScriptForm>({
    mode: "onChange",
    defaultValues: {
      scriptId: null,
    },
  })

  const open = () => {
    dialogForm.reset({ scriptId: null })

    const dialogRef = dialog.open({
      title: t("script.selectScript"),
      description: <SelectScriptDialogBody />,
      form: dialogForm,
      async onSubmit(values: SelectScriptForm) {
        if (values.scriptId == null) return

        const script = await api.script.getById(values.scriptId)
        onLoad(script!)
        dialogRef.close()
      },
      size: "lg",
      submitButton: {
        label: t("script.load"),
      },
    })
  }

  return (
    <Button onClick={open} {...other}>
      <LuFolderOpen />
      {label ?? t("script.loadExisting")}
    </Button>
  )
}
