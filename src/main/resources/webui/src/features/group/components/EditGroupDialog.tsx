import api from "@/api"
import type { UpdateGroupPayload } from "@/api/group"
import { HttpStatus, NetshotError } from "@/api/httpClient"
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
  /** The group, as the list gives it (the dialog reads the full group). */
  group: Group
}

export default function EditGroupDialog(props: EditGroupDialogProps) {
  const { group } = props
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialogConfig = useDialogConfig()
  const isStatic = group.type === GroupType.Static

  const title = useMemo(() => {
    return t("group.edit", {
      type: isStatic ? t("group.type.static") : t("group.type.dynamic"),
    })
  }, [t, isStatic])

  const form = useForm<GroupForm>({
    mode: "onChange",
    defaultValues: {
      name: group.name,
      folder: group.folder,
      visibleInReports: !group.hiddenFromReports,
      staticDevices: [],
      query: group.query ?? "",
    },
  })

  // The full group (its version, visibility in reports and query)
  const { data: full } = useQuery({
    queryKey: [QUERIES.GROUP_DETAIL, group.id],
    queryFn: async () => api.group.getWithETag(group.id),
    gcTime: 0,
  })

  useEffect(() => {
    if (full) {
      form.reset({
        ...form.getValues(),
        name: full.group.name,
        folder: full.group.folder,
        visibleInReports: !full.group.hiddenFromReports,
        query: full.group.query ?? "",
      })
    }
  }, [full, form])

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
    queryFn: async () => api.group.getAllDevices(group.id),
    enabled: isStatic,
    gcTime: 0,
  })

  useEffect(() => {
    if (staticDevices) {
      form.setValue("staticDevices", staticDevices)
    }
  }, [staticDevices, form])

  const ready = Boolean(full) && (!isStatic || Boolean(staticDevices))

  const { mutate, isPending } = useMutation({
    mutationFn: async (values: GroupForm) => {
      const changes: UpdateGroupPayload = {
        name: values.name,
        folder: values.folder,
        hiddenFromReports: !values.visibleInReports,
      }

      if (isStatic) {
        // The members are sent only when the user changed them (absent: unchanged)
        const ids = values.staticDevices.map((d) => d.id)
        const initialIds = (staticDevices ?? []).map((d) => d.id)
        if (ids.length !== initialIds.length || ids.some((id, i) => id !== initialIds[i])) {
          changes.devices = ids
        }
      } else {
        changes.query = values.query
      }

      await api.group.update(group.id, changes, full?.group.version)
    },
    onSuccess() {
      dialogConfig.close()
      queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_GROUPS] })
      queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_LIST] })
    },
    onError(err: NetshotError) {
      if (err.response?.status === HttpStatus.PreconditionFailed) {
        // Changed by someone else since it was read: reload the group
        toast.error({ ...err, description: t("group.changedMeanwhile") })
        queryClient.invalidateQueries({ queryKey: [QUERIES.GROUP_DETAIL, group.id] })
        queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_GROUPS] })
        return
      }
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
                    disabled={!isValid || !ready}
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
