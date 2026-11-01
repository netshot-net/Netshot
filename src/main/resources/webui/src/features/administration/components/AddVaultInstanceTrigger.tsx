import api from "@/api"
import { VaultInstanceRequest } from "@/api/admin"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { HttpsCaTrustMode, VaultAuthMethod, VaultInstanceType } from "@/types"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"
import VaultInstanceFormComponent, {
  VaultInstanceForm,
  VaultInstanceTestConnectionButton,
  buildVaultInstancePayload,
} from "./VaultInstanceForm"

export type AddVaultInstanceTriggerProps = { children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function AddVaultInstanceTrigger({ children, ...rest }: AddVaultInstanceTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_VAULT_INSTANCES)

  const form = useForm<VaultInstanceForm>({
    mode: "onChange",
    defaultValues: {
      name: "",
      type: VaultInstanceType.HashicorpKv2,
      baseUrl: "",
      namespace: "",
      httpsCaTrustMode: HttpsCaTrustMode.SystemTruststore,
      httpsCustomCaCertificate: "",
      kvMountPath: "secret",
      authMethod: VaultAuthMethod.AppRole,
      appRoleMountPath: "approle",
      appRoleId: "",
      appRoleSecretId: "",
      jwtMountPath: "jwt",
      jwtIdpTokenEndpoint: "",
      jwtClientId: "",
      jwtClientSecret: "",
      jwtVaultRole: "",
      jwtScope: "",
    },
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_VAULT_INSTANCE_CREATE,
    mutationFn: async (payload: VaultInstanceRequest) =>
      api.admin.createVaultInstance(payload),
    onError(err: NetshotError) {
      handleError(err, form)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.ADMIN_VAULT_INSTANCE_CREATE, {
      title: t("vault.create"),
      description: <VaultInstanceFormComponent />,
      footerStart: <VaultInstanceTestConnectionButton />,
      form,
      size: "lg",
      async onSubmit(values) {
        await mutation.mutateAsync(buildVaultInstancePayload(values))

        dialogRef.close()

        toast.success({
          title: t("common.success"),
          description: t("vault.successfullyCreated", {
            name: values.name,
          }),
        })

        form.reset()

        queryClient.invalidateQueries({
          queryKey: [QUERIES.ADMIN_VAULT_INSTANCES],
        })
      },
      onCancel() {
        form.reset()
      },
      submitButton: {
        label: t("common.create"),
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
