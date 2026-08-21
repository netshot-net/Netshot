import api, { UpdateGroupPayload } from "@/api"
import { NetshotError } from "@/api/httpClient"
import { QUERIES } from "@/constants"
import { useDialogConfig } from "@/dialog"
import { useToast } from "@/hooks"
import { Group, GroupType } from "@/types"
import { Button, CloseButton, Dialog, Portal, Stack } from "@chakra-ui/react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useCallback, useEffect, useMemo } from "react"
import { FormProvider, useForm, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import DynamicGroupForm from "./DynamicGroupForm"
import GroupInfoPanel from "./GroupInfoPanel"
import StaticGroupForm from "./StaticGroupForm"
import { GroupForm } from "../types"

export type EditGroupDialogProps = {
  group: Group
}

export default function EditGroupDialog(props: EditGroupDialogProps) {
  const { group } = props
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialogConfig = useDialogConfig()

  const title = useMemo(() => {
    return t("group.edit", {
      type: group.type === GroupType.Static ? t("group.type.static") : t("group.type.dynamic"),
    })
  }, [t, group])

  const form = useForm<GroupForm>({
    mode: "onChange",
    defaultValues: {
      name: group.name,
      folder: group.folder,
      visibleInReports: !group.hiddenFromReports,
      staticDevices: [],
      query: group.query,
    },
  })

  const name = useWatch({
    control: form.control,
    name: "name",
  })

  // Computed directly from the watched values rather than form.formState.isValid: with
  // mode "onChange" and a form pre-filled from an existing (already valid) group, RHF
  // never runs validation until a field changes, so isValid stays stuck at false.
  // folder is not required: "" is a legitimate root-level folder (see DeviceGroup.folder
  // default and createFoldersFromGroups.ts, which treats folder === "" as root).
  const isValid = Boolean(name?.trim())

  const { data: staticDevices } = useQuery({
    queryKey: [QUERIES.DEVICE_LIST, group.id, group.folder, group.name],
    queryFn: async () =>
      api.device.getAll({
        group: group.id,
      }),
  })

  useEffect(() => {
    if (staticDevices) {
      form.setValue("staticDevices", staticDevices)
    }
  }, [staticDevices, form])

  const { mutate, isPending } = useMutation({
    mutationFn: async (values: GroupForm) => {
      let payload: UpdateGroupPayload = {
        name: values.name,
        folder: values.folder,
        hiddenFromReports: !values.visibleInReports,
      }

      if (group.type === GroupType.Static) {
        payload.staticDevices = values.staticDevices.map((d) => d.id)
      } else if (group.type === GroupType.Dynamic) {
        payload = {
          ...payload,
          query: values.query,
        }
      }

      await api.group.update(group.id, payload)
    },
    onSuccess() {
      dialogConfig.close()
      queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_GROUPS] })
      queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_LIST] })
    },
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const submit = useCallback((values: GroupForm) => {
    mutate(values)
  }, [mutate])

  return (
    <FormProvider {...form}>
      <Dialog.Root
        preventScroll={false}
        placement="center"
        open={dialogConfig.props.isOpen}
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
        size="6xl"
      >
        <Portal>
          <Dialog.Backdrop />
          <Dialog.Positioner>
            <Dialog.Content h="55vh" as="form" onSubmit={form.handleSubmit(submit)}>
              <Dialog.Header as="h3" fontSize="2xl" fontWeight="semibold">
                {title}
                <Dialog.CloseTrigger asChild>
                  <CloseButton size="sm" variant="outline" />
                </Dialog.CloseTrigger>
              </Dialog.Header>
              <Dialog.Body flex="1" display="flex" overflowY="auto">
                <Stack direction="row" gap="9" flex="1">
                  <GroupInfoPanel groupType={group.type} />
                  {group.type === GroupType.Static && <StaticGroupForm />}
                  {group.type === GroupType.Dynamic && <DynamicGroupForm />}
                </Stack>
              </Dialog.Body>
              <Dialog.Footer>
                <Stack direction="row" gap="3">
                  <Button onClick={dialogConfig.close}>{t("common.cancel")}</Button>
                  <Button
                    type="submit"
                    disabled={!isValid}
                    loading={isPending}
                    variant="primary"
                  >
                    {t("common.applyChanges")}
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
