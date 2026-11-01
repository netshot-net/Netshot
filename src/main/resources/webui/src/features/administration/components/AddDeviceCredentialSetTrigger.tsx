import api from "@/api"
import { CredentialSetRequest } from "@/api/admin"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS, QUERIES as APP_QUERIES } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"
import { buildCredentialSetPayload } from "./credentialSetPayload"
import { useDeviceCredentialSetTypeOptions } from "../hooks"
import DeviceCredentialSetFormComponent, {
  DeviceCredentialSetForm,
} from "./DeviceCredentialSetForm"

export type AddDeviceCredentialSetTriggerProps = { children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function AddDeviceCredentialSetTrigger({ children, ...rest }: AddDeviceCredentialSetTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const deviceCredentialSetTypeOptions = useDeviceCredentialSetTypeOptions()
  const dialog = useFormDialogWithMutation()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_DEVICE_CREDENTIALS)

  const form = useForm<DeviceCredentialSetForm>({
    mode: "onChange",
    defaultValues: {
      name: "",
      username: "",
      mgmtDomain: null,
      community: "",
      type: deviceCredentialSetTypeOptions.getFirst()?.value,
      authKey: "",
      privKey: "",
      password: "",
      superPassword: "",
      privateKey: "",
      usernameVaultInstanceId: null,
      usernameVaultPath: "",
      passwordVaultInstanceId: null,
      passwordVaultPath: "",
      superPasswordVaultInstanceId: null,
      superPasswordVaultPath: "",
      privateKeyVaultInstanceId: null,
      privateKeyVaultPath: "",
      communityVaultInstanceId: null,
      communityVaultPath: "",
      authKeyVaultInstanceId: null,
      authKeyVaultPath: "",
      privKeyVaultInstanceId: null,
      privKeyVaultPath: "",
    },
  })

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_CREDENTIAL_SET_CREATE,
    mutationFn: async (payload: CredentialSetRequest) =>
      api.admin.createCredentialSet(payload),
    onError(err: NetshotError) {
      handleError(err, form)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.ADMIN_CREDENTIAL_SET_CREATE, {
      title: t("credential.create"),
      description: <DeviceCredentialSetFormComponent />,
      form,
      size: "lg",
      async onSubmit(values) {
        const payload = buildCredentialSetPayload(values, "create")

        await mutation.mutateAsync(payload)

        dialogRef.close()

        toast.success({
          title: t("common.success"),
          description: t("credential.successfullyCreated", {
            name: values.name,
          }),
        })

        form.reset()

        queryClient.invalidateQueries({
          queryKey: [QUERIES.ADMIN_DEVICE_CREDENTIALS],
        })
        queryClient.invalidateQueries({ queryKey: [APP_QUERIES.CREDENTIAL_SET_LIST] })
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
