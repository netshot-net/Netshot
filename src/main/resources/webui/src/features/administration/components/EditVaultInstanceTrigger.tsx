import api from "@/api"
import { VaultInstance, VaultInstanceRequest } from "@/api/admin"
import type { Brief } from "@/api/client"
import { PASSWORD_UNCHANGED } from "@/components/FormControl"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { HttpsCaTrustMode, VaultAuthMethod, VaultInstanceType } from "@/types"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useRef } from "react"
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

/** The form values of a Vault instance (its secrets are write-only: left unchanged unless typed). */
function formValues(full: VaultInstance): Partial<VaultInstanceForm> {
  return {
    name: full.name ?? "",
    type: full.type as VaultInstanceType,
    baseUrl: full.baseUrl ?? "",
    namespace: full.namespace ?? "",
    httpsCaTrustMode: full.httpsCaTrustMode as HttpsCaTrustMode,
    httpsCustomCaCertificate: full.httpsCustomCaCertificate ?? "",
    kvMountPath: full.kvMountPath ?? "secret",
    authMethod: full.authMethod as VaultAuthMethod,
    appRoleMountPath: full.appRoleMountPath ?? "approle",
    appRoleId: full.appRoleId ?? "",
    appRoleSecretId: PASSWORD_UNCHANGED,
    jwtMountPath: full.jwtMountPath ?? "jwt",
    jwtIdpTokenEndpoint: full.jwtIdpTokenEndpoint ?? "",
    jwtClientId: full.jwtClientId ?? "",
    jwtClientSecret: PASSWORD_UNCHANGED,
    jwtVaultRole: full.jwtVaultRole ?? "",
    jwtScope: full.jwtScope ?? "",
  }
}

export type EditVaultInstanceTriggerProps = { vaultInstance: Brief["VaultInstance"]; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditVaultInstanceTrigger({ vaultInstance, children, ...rest }: EditVaultInstanceTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_VAULT_INSTANCES)

  // The full Vault instance, read when the dialog opens (its version is sent back with the changes)
  const fullRef = useRef<VaultInstance | null>(null)

  const form = useForm<VaultInstanceForm>({
    mode: "onChange",
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_VAULT_INSTANCE_UPDATE,
    mutationFn: async (payload: VaultInstanceRequest) =>
      api.admin.updateVaultInstance(vaultInstance.id, payload, fullRef.current?.version),
    onError(err: NetshotError) {
      handleError(err, form)
    },
  })

  const open = async () => {
    let full: VaultInstance
    try {
      full = await api.admin.getVaultInstance(vaultInstance.id)
    } catch (err) {
      toast.error(err as NetshotError)
      return
    }
    fullRef.current = full
    form.reset(formValues(full))
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
