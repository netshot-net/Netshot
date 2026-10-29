import { FormControl, VaultableInput } from "@/components"
import DomainSelect from "./DomainSelect"
import { FormControlType } from "@/components/FormControl"
import { Select } from "@/components/Select"
import { CredentialSetType, HashingAlgorithm, VaultableFieldRefs } from "@/types"
import { Stack } from "@chakra-ui/react"
import { useMemo } from "react"
import { useFormContext, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useVaultInstances } from "../api"
import {
  useDeviceCredentialSetAuthTypeOptions,
  useDeviceCredentialSetPrivateKeyTypeOptions,
  useDeviceCredentialSetTypeOptions,
} from "../hooks"

export type DeviceCredentialSetForm = {
  name: string
  mgmtDomain: number | null
  community?: string | null
  type: CredentialSetType
  authKey?: string | null
  authType?: HashingAlgorithm
  privKey?: string | null
  privType?: HashingAlgorithm
  username?: string
  password?: string | null
  superPassword?: string | null
  privateKey?: string | null
} & VaultableFieldRefs

export type DeviceCredentialSetFormProps = {
  freezeType?: boolean
  freezePasswords?: boolean
}

export default function DeviceCredentialSetForm(props: DeviceCredentialSetFormProps) {
  const { freezeType = false, freezePasswords = false } = props
  const form = useFormContext<DeviceCredentialSetForm>()
  const { t } = useTranslation()
  const deviceCredentialSetTypeOptions = useDeviceCredentialSetTypeOptions()
  const deviceCredentialSetAuthTypeOptions = useDeviceCredentialSetAuthTypeOptions()
  const deviceCredentialSetPrivateKeyTypeOptions = useDeviceCredentialSetPrivateKeyTypeOptions()
  const { data: vaultInstanceList } = useVaultInstances()

  const vaultInstances = useMemo(
    () => (vaultInstanceList ?? []).map((instance) => ({ label: instance.name, value: instance.id })),
    [vaultInstanceList]
  )

  const type = useWatch({
    control: form.control,
    name: "type",
  })
  const authType = useWatch({
    control: form.control,
    name: "authType",
  })
  const privType = useWatch({
    control: form.control,
    name: "privType",
  })
  const hasAuth = authType !== HashingAlgorithm.NONE
  const hasPriv = hasAuth && privType !== HashingAlgorithm.NONE

  return (
    <Stack gap="6">
      <FormControl
        label={t("common.name")}
        placeholder={t("common.eG", { example: t("credential.namePlaceholder") })}
        required
        control={form.control}
        name="name"
      />
      <DomainSelect
        control={form.control}
        name="mgmtDomain"
        label={t("domain.label")}
        withAny
      />
      <Select
        required
        disabled={freezeType}
        control={form.control}
        name="type"
        options={deviceCredentialSetTypeOptions.options}
        label={t("common.type")}
        placeholder={t("network.selectProtocol")}
      />
      {type === CredentialSetType.SNMP_V3 && (
        <>
          <VaultableInput
            label={t("user.username")}
            placeholder={t("common.eG", { example: "admin" })}
            required
            control={form.control}
            setValue={form.setValue}
            name="username"
            vaultInstances={vaultInstances}
          />
          <Select
            required
            fieldProps={{ w: hasAuth ? "50%" : "full" }}
            control={form.control}
            name="authType"
            options={deviceCredentialSetAuthTypeOptions.options}
            label={t("credential.authType")}
            onSelectItem={(value) => {
              if (value === HashingAlgorithm.NONE) {
                form.setValue("privType", HashingAlgorithm.NONE)
                form.setValue("privKey", "")
                form.setValue("authKey", "")
              }
            }}
          />
          {hasAuth && (
            <VaultableInput
              label={t("network.authKeySecret")}
              placeholder={t("common.eG", { example: t("credential.secretKey") })}
              fieldType={FormControlType.Password}
              required
              allowUnchanged={freezePasswords}
              control={form.control}
              setValue={form.setValue}
              name="authKey"
              vaultInstances={vaultInstances}
            />
          )}
          <Select
            required
            disabled={!hasAuth}
            fieldProps={{ w: hasPriv ? "50%" : "full" }}
            control={form.control}
            name="privType"
            options={deviceCredentialSetPrivateKeyTypeOptions.options}
            label={t("credential.privType")}
            onSelectItem={(value) => {
              if (value === HashingAlgorithm.NONE) {
                form.setValue("privKey", "")
              }
            }}
          />
          {hasPriv && (
            <VaultableInput
              label={t("network.privKeySecret")}
              placeholder={t("common.eG", { example: t("credential.secretKey") })}
              fieldType={FormControlType.Password}
              required
              allowUnchanged={freezePasswords}
              control={form.control}
              setValue={form.setValue}
              name="privKey"
              vaultInstances={vaultInstances}
            />
          )}
        </>
      )}
      {[CredentialSetType.SSH, CredentialSetType.Telnet].includes(type) && (
        <>
          <VaultableInput
            required
            label={t("user.username")}
            placeholder={t("common.eG", { example: "admin" })}
            control={form.control}
            setValue={form.setValue}
            name="username"
            vaultInstances={vaultInstances}
          />
          <VaultableInput
            required
            allowUnchanged={freezePasswords}
            fieldType={FormControlType.Password}
            label={t("auth.password")}
            placeholder={t("auth.typeYourPassword")}
            control={form.control}
            setValue={form.setValue}
            name="password"
            vaultInstances={vaultInstances}
          />
          <VaultableInput
            required
            allowUnchanged={freezePasswords}
            fieldType={FormControlType.Password}
            label={t("network.superPassword")}
            placeholder={t("network.typeSuperPassword")}
            control={form.control}
            setValue={form.setValue}
            name="superPassword"
            vaultInstances={vaultInstances}
          />
        </>
      )}
      {type === CredentialSetType.SSHKey && (
        <>
          <VaultableInput
            required
            label={t("user.username")}
            placeholder={t("common.eG", { example: "admin" })}
            control={form.control}
            setValue={form.setValue}
            name="username"
            vaultInstances={vaultInstances}
          />
          <VaultableInput
            required
            allowUnchanged={freezePasswords}
            autosize
            mono
            rows={2}
            fieldType={FormControlType.LongText}
            label={t("network.sshPrivateKey")}
            placeholder={t("network.typePrivateKey")}
            control={form.control}
            setValue={form.setValue}
            name="privateKey"
            vaultInstances={vaultInstances}
          />
          <VaultableInput
            allowUnchanged={freezePasswords}
            fieldType={FormControlType.Password}
            label={t("network.passphrase")}
            placeholder={t("network.typePassphrase")}
            control={form.control}
            setValue={form.setValue}
            name="password"
            vaultInstances={vaultInstances}
          />
          <VaultableInput
            required
            allowUnchanged={freezePasswords}
            fieldType={FormControlType.Password}
            label={t("network.superPassword")}
            placeholder={t("network.typeSuperPassword")}
            control={form.control}
            setValue={form.setValue}
            name="superPassword"
            vaultInstances={vaultInstances}
          />
        </>
      )}
      {[CredentialSetType.SNMP_V1, CredentialSetType.SNMP_V2C].includes(
        type
      ) && (
        <VaultableInput
          label={t("common.community")}
          placeholder={t("common.eG", { example: "public" })}
          fieldType={FormControlType.Password}
          required
          allowUnchanged={freezePasswords}
          control={form.control}
          setValue={form.setValue}
          name="community"
          vaultInstances={vaultInstances}
        />
      )}
      {type === CredentialSetType.HTTP && (
        <>
          <VaultableInput
            label={t("user.username")}
            placeholder={t("common.eG", { example: "admin" })}
            control={form.control}
            setValue={form.setValue}
            name="username"
            vaultInstances={vaultInstances}
            helperText={t("credential.httpUsernameHelperText")}
          />
          <VaultableInput
            required
            allowUnchanged={freezePasswords}
            fieldType={FormControlType.Password}
            label={t("auth.password")}
            placeholder={t("auth.typeYourPassword")}
            control={form.control}
            setValue={form.setValue}
            name="password"
            vaultInstances={vaultInstances}
            helperText={t("credential.httpPasswordHelperText")}
          />
        </>
      )}
    </Stack>
  )
}
