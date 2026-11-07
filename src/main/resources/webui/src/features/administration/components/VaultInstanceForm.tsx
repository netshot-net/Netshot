import api from "@/api"
import { VaultInstanceRequest } from "@/api/admin"
import { NetshotError } from "@/api/httpClient"
import { FormControl } from "@/components"
import { FormControlType, PASSWORD_UNCHANGED } from "@/components/FormControl"
import { Select } from "@/components/Select"
import { MUTATIONS } from "@/constants"
import { useToast } from "@/hooks"
import { HttpsCaTrustMode, VaultAuthMethod, VaultInstanceType } from "@/types"
import { Button, Stack } from "@chakra-ui/react"
import { useMutation } from "@tanstack/react-query"
import { useFormContext, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useVaultAuthMethodOptions, useVaultInstanceTypeOptions } from "../hooks"

export type VaultInstanceForm = {
  name: string
  type: VaultInstanceType
  baseUrl: string
  namespace: string
  httpsCaTrustMode: HttpsCaTrustMode
  httpsCustomCaCertificate: string
  kvMountPath: string
  authMethod: VaultAuthMethod
  appRoleMountPath: string
  appRoleId: string
  appRoleSecretId: string | null
  jwtMountPath: string
  jwtIdpTokenEndpoint: string
  jwtClientId: string
  jwtClientSecret: string | null
  jwtVaultRole: string
  jwtScope: string
}

export type VaultInstanceFormProps = {
  /** Edit mode: the type can't be changed once the instance is created. */
  freezeType?: boolean
  /** Edit mode: secret fields show the "unchanged" state instead of requiring a fresh value. */
  freezeSecrets?: boolean
}

const HTTPS_CA_TRUST_MODE_OPTIONS = [
  {
    label: "device.httpsCaTrustModeSystemTruststore",
    description: "device.httpsCaTrustModeSystemTruststoreDescription",
    value: HttpsCaTrustMode.SystemTruststore,
  },
  {
    label: "device.httpsCaTrustModeCustomCa",
    description: "device.httpsCaTrustModeCustomCaDescription",
    value: HttpsCaTrustMode.CustomCa,
  },
  {
    label: "device.httpsCaTrustModeTrustAny",
    description: "device.httpsCaTrustModeTrustAnyDescription",
    value: HttpsCaTrustMode.TrustAny,
  },
] as const

export function buildVaultInstancePayload(values: VaultInstanceForm): VaultInstanceRequest {
  let payload = {
    name: values.name,
    type: values.type,
    baseUrl: values.baseUrl,
    namespace: values.namespace || undefined,
    httpsCaTrustMode: values.httpsCaTrustMode,
    httpsCustomCaCertificate:
      values.httpsCaTrustMode === HttpsCaTrustMode.CustomCa
        ? values.httpsCustomCaCertificate
        : undefined,
    kvMountPath: values.kvMountPath,
    authMethod: values.authMethod,
  } as VaultInstanceRequest

  if (values.authMethod === VaultAuthMethod.AppRole) {
    payload = {
      ...payload,
      appRoleMountPath: values.appRoleMountPath,
      appRoleId: values.appRoleId,
      appRoleSecretId: values.appRoleSecretId ?? undefined,
    }
  } else if (values.authMethod === VaultAuthMethod.Jwt) {
    payload = {
      ...payload,
      jwtMountPath: values.jwtMountPath,
      jwtIdpTokenEndpoint: values.jwtIdpTokenEndpoint,
      jwtClientId: values.jwtClientId,
      jwtClientSecret: values.jwtClientSecret ?? undefined,
      jwtVaultRole: values.jwtVaultRole,
      jwtScope: values.jwtScope || undefined,
    }
  }

  return payload
}

export default function VaultInstanceForm({ freezeType = false, freezeSecrets = false }: VaultInstanceFormProps) {
  const { t } = useTranslation()
  const form = useFormContext<VaultInstanceForm>()
  const vaultAuthMethodOptions = useVaultAuthMethodOptions()
  const vaultInstanceTypeOptions = useVaultInstanceTypeOptions()

  const authMethod = useWatch({ control: form.control, name: "authMethod" })
  const httpsCaTrustMode = useWatch({ control: form.control, name: "httpsCaTrustMode" })

  return (
    <Stack gap="6">
      <FormControl
        label={t("common.name")}
        placeholder={t("common.eG", { example: t("vault.namePlaceholder") })}
        required
        control={form.control}
        name="name"
      />
      <Select
        required
        disabled={freezeType}
        label={t("common.type")}
        control={form.control}
        name="type"
        options={vaultInstanceTypeOptions.options}
      />
      <FormControl
        label={t("vault.baseUrl")}
        placeholder="https://vault.example.com:8200"
        type={FormControlType.Url}
        required
        control={form.control}
        name="baseUrl"
      />
      <FormControl
        label={t("vault.namespace")}
        helperText={t("vault.namespaceHelperText")}
        control={form.control}
        name="namespace"
      />
      <FormControl
        label={t("vault.kvMountPath")}
        placeholder="secret"
        required
        control={form.control}
        name="kvMountPath"
      />
      <Stack direction="column" gap="3">
        <Select
          label={t("device.httpsCaTrustMode")}
          control={form.control}
          name="httpsCaTrustMode"
          options={HTTPS_CA_TRUST_MODE_OPTIONS.map((option) => ({
            label: t(option.label),
            description: t(option.description),
            value: option.value,
          }))}
        />
        {httpsCaTrustMode === HttpsCaTrustMode.CustomCa && (
          <FormControl
            autosize
            mono
            clearable
            required
            rows={4}
            type={FormControlType.LongText}
            label={t("device.httpsCustomCaCertificate")}
            placeholder={t("device.httpsCustomCaCertificatePlaceholder")}
            control={form.control}
            name="httpsCustomCaCertificate"
          />
        )}
      </Stack>
      <Select
        required
        label={t("vault.authMethod")}
        control={form.control}
        name="authMethod"
        options={vaultAuthMethodOptions.options}
      />
      {authMethod === VaultAuthMethod.AppRole && (
        <>
          <FormControl
            label={t("vault.appRoleMountPath")}
            helperText={t("vault.appRoleMountPathHelperText")}
            placeholder="approle"
            required
            control={form.control}
            name="appRoleMountPath"
          />
          <FormControl
            label={t("vault.roleId")}
            required
            control={form.control}
            name="appRoleId"
          />
          <FormControl
            label={t("vault.secretId")}
            type={FormControlType.Password}
            required={!freezeSecrets}
            allowUnchanged={freezeSecrets}
            control={form.control}
            name="appRoleSecretId"
          />
        </>
      )}
      {authMethod === VaultAuthMethod.Jwt && (
        <>
          <FormControl
            label={t("vault.jwtMountPath")}
            helperText={t("vault.jwtMountPathHelperText")}
            placeholder="jwt"
            required
            control={form.control}
            name="jwtMountPath"
          />
          <FormControl
            label={t("vault.idpTokenEndpoint")}
            type={FormControlType.Url}
            required
            control={form.control}
            name="jwtIdpTokenEndpoint"
          />
          <FormControl
            label={t("vault.clientId")}
            required
            control={form.control}
            name="jwtClientId"
          />
          <FormControl
            label={t("vault.clientSecret")}
            type={FormControlType.Password}
            required={!freezeSecrets}
            allowUnchanged={freezeSecrets}
            control={form.control}
            name="jwtClientSecret"
          />
          <FormControl
            label={t("vault.vaultRole")}
            helperText={t("vault.vaultRoleHelperText")}
            required
            control={form.control}
            name="jwtVaultRole"
          />
          <FormControl label={t("vault.scope")} control={form.control} name="jwtScope" />
        </>
      )}
    </Stack>
  )
}

export type VaultInstanceTestConnectionButtonProps = {
  /** ID of the Vault instance being edited, so the test can fall back to its stored secret when unchanged. Omitted when adding a new one. */
  id?: number
  /** Edit mode: secret fields hold the "unchanged" sentinel rather than a fresh value - still counts as filled in. */
  freezeSecrets?: boolean
}

/**
 * Rendered as the dialog's footerStart, left of the Cancel/Submit buttons.
 * Kept separate from the main form so it can watch just the fields it needs
 * and stay enabled only once there's enough to actually attempt a connection.
 */
export function VaultInstanceTestConnectionButton({
  id,
  freezeSecrets = false,
}: VaultInstanceTestConnectionButtonProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const form = useFormContext<VaultInstanceForm>()

  const baseUrl = useWatch({ control: form.control, name: "baseUrl" })
  const kvMountPath = useWatch({ control: form.control, name: "kvMountPath" })
  const authMethod = useWatch({ control: form.control, name: "authMethod" })
  const appRoleMountPath = useWatch({ control: form.control, name: "appRoleMountPath" })
  const appRoleId = useWatch({ control: form.control, name: "appRoleId" })
  const appRoleSecretId = useWatch({ control: form.control, name: "appRoleSecretId" })
  const jwtMountPath = useWatch({ control: form.control, name: "jwtMountPath" })
  const jwtIdpTokenEndpoint = useWatch({ control: form.control, name: "jwtIdpTokenEndpoint" })
  const jwtClientId = useWatch({ control: form.control, name: "jwtClientId" })
  const jwtClientSecret = useWatch({ control: form.control, name: "jwtClientSecret" })
  const jwtVaultRole = useWatch({ control: form.control, name: "jwtVaultRole" })

  const isSecretFilled = (value: string | null) =>
    !!value || (freezeSecrets && value === PASSWORD_UNCHANGED)

  const hasAuthDetails =
    authMethod === VaultAuthMethod.AppRole
      ? !!appRoleMountPath && !!appRoleId && isSecretFilled(appRoleSecretId)
      : !!jwtMountPath &&
        !!jwtIdpTokenEndpoint &&
        !!jwtClientId &&
        !!jwtVaultRole &&
        isSecretFilled(jwtClientSecret)

  const canTest = !!baseUrl && !!kvMountPath && hasAuthDetails

  const testMutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_VAULT_INSTANCE_TEST,
    mutationFn: async () => {
      const values = form.getValues()
      const secretUnchanged =
        values.authMethod === VaultAuthMethod.AppRole
          ? values.appRoleSecretId === PASSWORD_UNCHANGED
          : values.jwtClientSecret === PASSWORD_UNCHANGED
      // A secret which is left unchanged is only known by the server: test the saved instance
      return id !== undefined && secretUnchanged
        ? api.admin.testSavedVaultInstance(id)
        : api.admin.testVaultInstance(buildVaultInstancePayload(values))
    },
    onSuccess(result) {
      if (result.success) {
        toast.success({
          title: t("common.success"),
          description: t("vault.testConnectionSuccess"),
        })
      } else {
        toast.error({
          title: t("common.error"),
          description: result.message ?? t("common.netshotServerError"),
        })
      }
    },
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  return (
    <Button
      variant="outline"
      loading={testMutation.isPending}
      disabled={!canTest}
      onClick={() => testMutation.mutate()}
    >
      {t("vault.testConnection")}
    </Button>
  )
}
