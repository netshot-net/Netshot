import React, { MouseEvent } from "react"
import Slot from "@/components/Slot"
import api from "@/api"
import { HttpStatus, NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { Group } from "@/types"
import { Alert, Stack, Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Trans, useTranslation } from "react-i18next"

export type RemoveGroupTriggerProps = { group: Group; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function RemoveGroupTrigger({ group, children, ...rest }: RemoveGroupTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.GROUP_REMOVE,
    mutationFn: async (target: Group) => {
      // The brief view of the list has no version: delete the group as it is now
      const { group: current } = await api.group.getWithETag(target.id)
      await api.group.remove(target.id, current.version)
    },
    onError(err: NetshotError) {
      if (err.response?.status === HttpStatus.PreconditionFailed) {
        toast.error({ ...err, description: t("group.changedMeanwhile") })
        queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_GROUPS] })
        return
      }
      toast.error(err)
    },
  })

  const open = (e: MouseEvent) => {
    e?.stopPropagation()
    const dialogRef = dialog.open(MUTATIONS.GROUP_REMOVE, {
      title: t("group.remove"),
      description: (
        <Stack gap="5">
          <Text>
            <Trans
              t={t}
              i18nKey="group.aboutToRemove"
              values={{ name: group.name }}
              components={{ bold: <Text as="span" fontWeight="semibold" /> }}
            />
          </Text>
          <Alert.Root color="yellow.900" status="warning">
            <Alert.Indicator />
            <Alert.Title>{t("group.removeWarning")}</Alert.Title>
          </Alert.Root>
        </Stack>
      ),
      async onConfirm() {
        await mutation.mutateAsync(group)
        queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_GROUPS] })
        dialogRef.close()
      },
      confirmButton: {
        label: t("common.remove"),
        props: {
          colorPalette: "red",
        },
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
