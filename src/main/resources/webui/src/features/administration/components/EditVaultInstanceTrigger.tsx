import api from "@/api"
import { VaultInstance, VaultInstanceRequest } from "@/api/admin"
import { PASSWORD_UNCHANGED } from "@/components/FormControl"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { HttpsCaTrustMode, VaultAuthMethod, VaultInstanceType } from "@/types"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useEffect, useMemo } from "react"
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

export type EditVaultInstanceTriggerProps = { vaultInstance: VaultInstance; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditVaultInstanceTrigger({ vaultInstance, children, ...rest }: EditVaultInstanceTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_VAULT_INSTANCES)

  const defaultValues = useMemo<Partial<VaultInstanceForm>>(
    () => ({
      name: vaultInstance.name ?? "",
      type: vaultInstance.type as VaultInstanceType,
      baseUrl: vaultInstance.baseUrl ?? "",
      namespace: vaultInstance.namespace ?? "",
      httpsCaTrustMode: vaultInstance.httpsCaTrustMode as HttpsCaTrustMode,
      httpsCustomCaCertificate: vaultInstance.httpsCustomCaCertificate ?? "",
      kvMountPath: vaultInstance.kvMountPath ?? "secret",
      authMethod: vaultInstance.authMethod as VaultAuthMethod,
      appRoleMountPath: vaultInstance.appRoleMountPath ?? "approle",
      appRoleId: vaultInstance.appRoleId ?? "",
      appRoleSecretId: PASSWORD_UNCHANGED,
      jwtMountPath: vaultInstance.jwtMountPath ?? "jwt",
      jwtIdpTokenEndpoint: vaultInstance.jwtIdpTokenEndpoint ?? "",
      jwtClientId: vaultInstance.jwtClientId ?? "",
      jwtClientSecret: PASSWORD_UNCHANGED,
      jwtVaultRole: vaultInstance.jwtVaultRole ?? "",
      jwtScope: vaultInstance.jwtScope ?? "",
    }),
    [vaultInstance]
  )

  const form = useForm<VaultInstanceForm>({
    mode: "onChange",
    defaultValues,
  })

  useEffect(() => {
    form.reset(defaultValues)
  }, [defaultValues, form])

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_VAULT_INSTANCE_UPDATE,
    mutationFn: async (payload: VaultInstanceRequest) =>
      api.admin.updateVaultInstance(vaultInstance.id!, payload, vaultInstance.version),
    onError(err: NetshotError) {
      handleError(err, form)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.ADMIN_VAULT_INSTANCE_UPDATE, {
      title: t("vault.edit"),
      description: <VaultInstanceFormComponent freezeType freezeSecrets />,
      footerStart: (
        <VaultInstanceTestConnectionButton id={vaultInstance.id} freezeSecrets />
      ),
      form,
      size: "lg",
      async onSubmit(values) {
        // buildVaultInstancePayload already turns the PASSWORD_UNCHANGED (null)
        // sentinel into `undefined`, which is dropped from the JSON body -
        // consistent with the "unchanged" convention used for other secrets.
        await mutation.mutateAsync(buildVaultInstancePayload(values))

        dialogRef.close()
        form.reset()

        toast.success({
          title: t("common.success"),
          description: t("vault.successfullyUpdated", {
            name: values.name,
          }),
        })

        queryClient.invalidateQueries({
          queryKey: [QUERIES.ADMIN_VAULT_INSTANCES],
        })
      },
      onCancel() {
        form.reset()
      },
      submitButton: {
        label: t("common.applyChanges"),
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
