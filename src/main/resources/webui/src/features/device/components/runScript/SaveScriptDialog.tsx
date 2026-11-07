import api from "@/api"
import { Schemas } from "@/api/client"
import { NetshotError } from "@/api/httpClient"
import { FormControl } from "@/components"
import { useTreeOpenKeys } from "@/hooks"
import { MUTATIONS, QUERIES } from "@/constants"
import { useDialogConfig } from "@/dialog"
import { useToast } from "@/hooks"
import { ScriptBrief } from "@/types"
import { createFoldersFromScripts } from "@/utils"
import { Box, Button, Center, CloseButton, Dialog, Heading, Portal, Stack, Text } from "@chakra-ui/react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useCallback, useMemo } from "react"
import { FormProvider, useForm, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { TreeScript } from "../scriptTree"

type SaveScriptForm = {
  name: string
  folder: string
}

export type SaveScriptDialogProps = {
  initialName?: string
  initialFolder?: string
  driver: string
  scriptContent: string
  onSaved(script: ScriptBrief): void
}

export default function SaveScriptDialog(props: SaveScriptDialogProps) {
  const { initialName = "", initialFolder = "", driver, scriptContent, onSaved } = props
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialogConfig = useDialogConfig()

  const form = useForm<SaveScriptForm>({
    mode: "onChange",
    defaultValues: { name: initialName, folder: initialFolder },
  })

  const { data: scripts } = useQuery({
    queryKey: [QUERIES.SCRIPT_LIST],
    queryFn: async () => api.script.getAll(),
  })

  const name = useWatch({ control: form.control, name: "name" })
  const folder = useWatch({ control: form.control, name: "folder" })

  const items = useMemo(() => createFoldersFromScripts(scripts ?? []), [scripts])
  const { isOpen, toggle } = useTreeOpenKeys()

  const onScriptSelect = useCallback(
    (script: ScriptBrief) => {
      form.setValue("name", script.name, { shouldValidate: true })
      form.setValue("folder", script.folder ?? "", { shouldValidate: true })
    },
    [form]
  )

  const onFolderSelect = useCallback(
    (path: string) => {
      form.setValue("folder", path, { shouldValidate: true })
    },
    [form]
  )

  const duplicate = useMemo(() => {
    const trimmedName = (name ?? "").trim()
    if (!trimmedName) return undefined
    return scripts?.find(
      (s) => s.name === trimmedName && (s.folder ?? "") === (folder ?? "").trim()
    )
  }, [scripts, name, folder])

  const mutation = useMutation({
    mutationKey: MUTATIONS.SCRIPT_UPDATE,
    mutationFn: async (payload: { matchId?: number; script: Schemas["ScriptRequest"] }) => {
      if (payload.matchId != null) {
        await api.script.remove(payload.matchId)
      }
      return api.script.create(payload.script)
    },
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  function close() {
    dialogConfig.close()
  }

  async function submit(values: SaveScriptForm) {
    const saved = await mutation.mutateAsync({
      matchId: duplicate?.id,
      script: {
        name: values.name.trim(),
        folder: values.folder.trim(),
        deviceDriver: driver,
        script: scriptContent,
      },
    })

    await queryClient.invalidateQueries({ queryKey: [QUERIES.SCRIPT_LIST] })

    close()
    onSaved(saved!)
  }

  return (
    <FormProvider {...form}>
      <Dialog.Root
        open={dialogConfig.props.isOpen}
        placement="center"
        motionPreset="slide-in-bottom"
        size={dialogConfig.props.size}
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
                  {t("script.saveAs")}
                </Heading>
                <CloseButton size="sm" variant="outline" onClick={close} />
              </Dialog.Header>
              <Dialog.Body>
                <Stack gap="5">
                  <FormControl
                    required
                    label={t("common.name")}
                    control={form.control}
                    name="name"
                    placeholder={t("common.eG", { example: t("script.namePlaceholder") })}
                  />
                  <FormControl
                    label={t("script.folder")}
                    control={form.control}
                    name="folder"
                    placeholder={t("group.folderExample")}
                    helperText={t("group.useSlashesForFolder")}
                  />
                  <Box
                    maxH="280px"
                    overflow="auto"
                    borderWidth="1px"
                    borderColor="grey.100"
                    borderRadius="xl"
                    p="3"
                  >
                    {items.length === 0 ? (
                      <Center py="4">
                        <Text color="fg.muted">{t("script.noScriptFound")}</Text>
                      </Center>
                    ) : (
                      <TreeScript
                        items={items}
                        onScriptSelect={onScriptSelect}
                        onFolderSelect={onFolderSelect}
                        isFolderOpen={isOpen}
                        toggleFolderOpen={toggle}
                        showMenu
                      />
                    )}
                  </Box>
                </Stack>
              </Dialog.Body>
              <Dialog.Footer justifyContent="flex-end">
                <Stack direction="row" gap="3">
                  <Button onClick={close}>{t("common.cancel")}</Button>
                  <Button
                    type="submit"
                    variant="primary"
                    colorPalette={duplicate ? "red" : undefined}
                    disabled={!form.formState.isValid}
                    loading={mutation.isPending}
                  >
                    {duplicate ? t("script.overwrite") : t("common.save")}
                  </Button>
                </Stack>
              </Dialog.Footer>
            </Dialog.Content>
          </Dialog.Positioner>
        </Portal>
      </Dialog.Root>
    </FormProvider>
  )
}
