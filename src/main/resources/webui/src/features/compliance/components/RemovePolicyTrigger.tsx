import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import type { Policy } from "@/api/policy"
import { Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { MouseEvent } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import React from "react"
import Slot from "@/components/Slot"

export type RemovePolicyTriggerProps = { policy: Policy; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function RemovePolicyTrigger({ policy, children, ...rest }: RemovePolicyTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const navigate = useNavigate()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.POLICY_REMOVE,
    mutationFn: async () => api.policy.remove(policy?.id),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const open = (evt: MouseEvent<HTMLButtonElement>) => {
    evt?.stopPropagation()
    const dialogRef = dialog.open(MUTATIONS.POLICY_REMOVE, {
      title: t("policy.remove"),
      description: (
        <Text>
          {t("policy.aboutToRemove", {
            policyName: policy?.name,
          })}
        </Text>
      ),
      async onConfirm() {
        await mutation.mutateAsync()
        queryClient.invalidateQueries({ queryKey: [QUERIES.POLICY_LIST] })
        queryClient.invalidateQueries({ queryKey: [QUERIES.POLICY_SEARCH_LIST] })
        navigate("/app/compliance")
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

  // Menu.Item already triggers `onClick` internally to fire `onSelect`, so binding
  // both to the same handler would call it twice; pick the one the child understands.
  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
