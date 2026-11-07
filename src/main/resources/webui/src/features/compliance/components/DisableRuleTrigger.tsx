import api from "@/api"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES as GLOBAL_QUERIES } from "@/constants"
import { useConfirmDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import type { Rule } from "@/api/rule"
import { Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"

export type DisableRuleTriggerProps = { policyId: number; rule: Rule; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function DisableRuleTrigger({ policyId, rule, children, ...rest }: DisableRuleTriggerProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const dialog = useConfirmDialogWithMutation()

  const mutation = useMutation({
    mutationKey: MUTATIONS.RULE_DISABLE,
    mutationFn: async () => api.rule.disable(rule.id),
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  function open() {
    const dialogRef = dialog.open(MUTATIONS.RULE_DISABLE, {
      title: t("policy.rule.disable"),
      description: (
        <Text>
          {t("policy.rule.aboutToDisable", {
            name: rule?.name,
          })}
        </Text>
      ),
      async onConfirm() {
        await mutation.mutateAsync()
        queryClient.invalidateQueries({ queryKey: [QUERIES.POLICY_RULE_LIST, policyId] })
        queryClient.invalidateQueries({ queryKey: [QUERIES.RULE_DETAIL, +policyId, rule.id] })
        queryClient.invalidateQueries({ queryKey: [GLOBAL_QUERIES.POLICY_LIST] })
        queryClient.invalidateQueries({ queryKey: [GLOBAL_QUERIES.POLICY_SEARCH_LIST] })

        dialogRef.close()

        toast.success({
          title: t("common.success"),
          description: t("policy.rule.successfullyDisabled", {
            name: rule?.name,
          }),
        })
      },
      confirmButton: {
        label: t("common.disable"),
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
