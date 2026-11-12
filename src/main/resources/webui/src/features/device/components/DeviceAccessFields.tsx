import { useDeviceCredentialSetAuthTypeOptions, useDeviceCredentialSetPrivateKeyTypeOptions } from "@/features/administration/hooks"
import FormControl, { FormControlType } from "@/components/FormControl"
import { Select } from "@/components/Select"
import Switch from "@/components/Switch"
import VaultableInput from "@/components/VaultableInput"
import BulkEditLockToggle from "./BulkEditLockToggle"
import { Brief, Schemas } from "@/api/client"
import { DeviceAccessDefinition, DeviceTypeProtocol, HashingAlgorithm, HttpsCaTrustMode, Option, SshHostKeyVerification, VaultableFieldRefs } from "@/types"
import { Collapsible, Separator, Stack, Text } from "@chakra-ui/react"
import { useEffect, useMemo, useState } from "react"
import { Control, useController, useFieldArray, UseFormSetValue, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuChevronRight } from "react-icons/lu"
import { useCredentialSets, useVaultInstanceChoices } from "../api"

/** The types of credential sets, as the API names them. */
export type CredentialSetType = NonNullable<Schemas["DeviceCredentialSetRequest"]["type"]>
type CredentialSet = Schemas["DeviceCredentialSetRequest"]

/** The Vault location of a secret in a request, if the field is backed by Vault. */
function vaultOf(instanceId: number | null | undefined, path: string | null | undefined) {
  return instanceId !== null && instanceId !== undefined ? { vaultInstance: instanceId, path: path ?? "" } : undefined
}

export type DeviceAccessCredentialMode = "none" | "global" | "specific"
export type SshAuthMethod = "password" | "key"

/** Sentinel `globalCredentialSetId` value meaning "try every eligible credential set" instead of a specific pick. */
export const TRY_ALL_CREDENTIALS_VALUE = "__try_all__"

export type DeviceAccessFormValue = {
  accessName: string
  protocol: string
  defaultPort: number
  /** Bulk-edit only: while true, this access is left untouched on every selected device and excluded from the submitted payload. */
  locked?: boolean
  overrideConnection: boolean
  address: string
  port: string
  mode: DeviceAccessCredentialMode
  globalCredentialSetId: string
  sshAuthMethod: SshAuthMethod
  username: string
  password: string | null
  superPassword: string | null
  privateKey: string | null
  community: string | null
  authType?: HashingAlgorithm
  authKey?: string | null
  privType?: HashingAlgorithm
  privKey?: string | null
  sshHostKeyVerification: SshHostKeyVerification
  sshTrustedHostKeys: string
  httpsCaTrustMode: HttpsCaTrustMode
  httpsCustomCaCertificate: string
} & VaultableFieldRefs

export type DeviceAccessFieldsProps = {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  control: Control<any>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  setValue: UseFormSetValue<any>
  accessDefinitions: DeviceAccessDefinition[] | undefined
  /** Bulk-edit mode: every row starts locked ("leave unchanged on all selected devices") and must be explicitly unlocked to be included in the payload. */
  bulk?: boolean
}

/** Known protocol acronyms, kept fully (or conventionally) cased when humanizing an access/group name. */
const ACCESS_NAME_ACRONYMS: Record<string, string> = {
  ssh: "SSH",
  telnet: "Telnet",
  http: "HTTP",
  https: "HTTPS",
  snmp: "SNMP",
  snmpv1: "SNMPv1",
  snmpv2c: "SNMPv2c",
  snmpv3: "SNMPv3",
}

/** Turns an access's camelCase JS key (e.g. "alternateSsh") into a readable label ("Alternate SSH"). */
function humanizeAccessName(name: string): string {
  const spaced = name.replace(/([a-z0-9])([A-Z])/g, "$1 $2")
  return spaced
    .split(" ")
    .map((word) => ACCESS_NAME_ACRONYMS[word.toLowerCase()] ?? word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ")
}

/** The credential type for a "define specific credentials" row, keyed by the access's protocol (SSH may instead resolve to SSH Key, see `sshAuthMethod`). */
export function getSpecificCredentialType(protocol: string): CredentialSetType | undefined {
  switch (protocol) {
    case DeviceTypeProtocol.Ssh:
      return "SSH"
    case DeviceTypeProtocol.Telnet:
      return "TELNET"
    case DeviceTypeProtocol.Http:
    case DeviceTypeProtocol.Https:
      return "HTTP"
    case DeviceTypeProtocol.SnmpV1:
      return "SNMP_V1"
    case DeviceTypeProtocol.SnmpV2c:
      return "SNMP_V2C"
    case DeviceTypeProtocol.SnmpV3:
      return "SNMP_V3"
    default:
      return undefined
  }
}

/** Same as {@link getSpecificCredentialType}, but for an SSH access, honors the row's password-vs-key choice. */
function getEffectiveSpecificCredentialType(protocol: string, sshAuthMethod: SshAuthMethod): CredentialSetType | undefined {
  if (protocol === DeviceTypeProtocol.Ssh && sshAuthMethod === "key") {
    return "SSH_KEY"
  }
  return getSpecificCredentialType(protocol)
}

/** Global credential sets compatible with an access's protocol (SSH accesses also accept SSH-key sets). */
function getCompatibleCredentialTypes(protocol: string): CredentialSetType[] {
  if (protocol === DeviceTypeProtocol.Ssh) {
    return ["SSH", "SSH_KEY"]
  }
  const type = getSpecificCredentialType(protocol)
  return type ? [type] : []
}

export type SpecificCredentialFieldsProps = {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  control: Control<any>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  setValue: UseFormSetValue<any>
  namePrefix: string
  type: CredentialSetType
  /** True when editing an access that already has a stored secret - shows the lock icon and preserves it unless explicitly changed. */
  allowUnchanged: boolean
  /** Available Vault instances, as VaultableInput options. */
  vaultInstances: Option<number>[]
}

/** Renders exactly the fields relevant to one credential type, nested under `namePrefix`. */
export function SpecificCredentialFields({
  control, setValue, namePrefix, type, allowUnchanged, vaultInstances,
}: SpecificCredentialFieldsProps) {
  const { t } = useTranslation()
  const authTypeOptions = useDeviceCredentialSetAuthTypeOptions()
  const privateKeyTypeOptions = useDeviceCredentialSetPrivateKeyTypeOptions()

  const authType = useWatch({ control, name: `${namePrefix}.authType` }) as HashingAlgorithm | undefined
  const privType = useWatch({ control, name: `${namePrefix}.privType` }) as HashingAlgorithm | undefined
  const hasAuth = authType !== HashingAlgorithm.NONE
  const hasPriv = hasAuth && privType !== HashingAlgorithm.NONE

  const { field: authKeyField } = useController({ control, name: `${namePrefix}.authKey` })
  const { field: privTypeField } = useController({ control, name: `${namePrefix}.privType` })
  const { field: privKeyField } = useController({ control, name: `${namePrefix}.privKey` })

  if (type === "SSH" || type === "TELNET") {
    return (
      <>
        <VaultableInput
          required
          label={t("user.username")}
          placeholder={t("common.eG", { example: "admin" })}
          control={control}
          setValue={setValue}
          name={`${namePrefix}.username`}
          vaultInstances={vaultInstances}
        />
        <VaultableInput
          required
          allowUnchanged={allowUnchanged}
          fieldType={FormControlType.Password}
          label={t("auth.password")}
          placeholder={t("auth.typeYourPassword")}
          control={control}
          setValue={setValue}
          name={`${namePrefix}.password`}
          vaultInstances={vaultInstances}
        />
        <VaultableInput
          allowUnchanged={allowUnchanged}
          fieldType={FormControlType.Password}
          label={t("network.superPassword")}
          placeholder={t("network.typeSuperPassword")}
          control={control}
          setValue={setValue}
          name={`${namePrefix}.superPassword`}
          vaultInstances={vaultInstances}
        />
      </>
    )
  }
  if (type === "SSH_KEY") {
    return (
      <>
        <VaultableInput
          required
          label={t("user.username")}
          placeholder={t("common.eG", { example: "admin" })}
          control={control}
          setValue={setValue}
          name={`${namePrefix}.username`}
          vaultInstances={vaultInstances}
        />
        <VaultableInput
          required
          allowUnchanged={allowUnchanged}
          autosize
          mono
          rows={2}
          fieldType={FormControlType.LongText}
          label={t("network.sshPrivateKey")}
          placeholder={t("network.typePrivateKey")}
          control={control}
          setValue={setValue}
          name={`${namePrefix}.privateKey`}
          vaultInstances={vaultInstances}
        />
        <VaultableInput
          allowUnchanged={allowUnchanged}
          fieldType={FormControlType.Password}
          label={t("network.passphrase")}
          placeholder={t("network.typePassphrase")}
          control={control}
          setValue={setValue}
          name={`${namePrefix}.password`}
          vaultInstances={vaultInstances}
        />
        <VaultableInput
          allowUnchanged={allowUnchanged}
          fieldType={FormControlType.Password}
          label={t("network.superPassword")}
          placeholder={t("network.typeSuperPassword")}
          control={control}
          setValue={setValue}
          name={`${namePrefix}.superPassword`}
          vaultInstances={vaultInstances}
        />
      </>
    )
  }
  if (type === "HTTP") {
    return (
      <>
        <VaultableInput
          label={t("user.username")}
          placeholder={t("common.eG", { example: "admin" })}
          control={control}
          setValue={setValue}
          name={`${namePrefix}.username`}
          vaultInstances={vaultInstances}
        />
        <VaultableInput
          required
          allowUnchanged={allowUnchanged}
          fieldType={FormControlType.Password}
          label={t("auth.password")}
          placeholder={t("auth.typeYourPassword")}
          control={control}
          setValue={setValue}
          name={`${namePrefix}.password`}
          vaultInstances={vaultInstances}
        />
      </>
    )
  }
  if (type === "SNMP_V1" || type === "SNMP_V2C") {
    return (
      <VaultableInput
        required
        allowUnchanged={allowUnchanged}
        fieldType={FormControlType.Password}
        label={t("common.community")}
        placeholder={t("common.eG", { example: "public" })}
        control={control}
        setValue={setValue}
        name={`${namePrefix}.community`}
        vaultInstances={vaultInstances}
      />
    )
  }
  if (type === "SNMP_V3") {
    return (
      <>
        <VaultableInput
          required
          label={t("user.username")}
          placeholder={t("common.eG", { example: "admin" })}
          control={control}
          setValue={setValue}
          name={`${namePrefix}.username`}
          vaultInstances={vaultInstances}
        />
        <Select
          required
          fieldProps={{ w: hasAuth ? "50%" : "full" }}
          control={control}
          name={`${namePrefix}.authType`}
          options={authTypeOptions.options}
          label={t("credential.authType")}
          onSelectItem={(value) => {
            if (value === HashingAlgorithm.NONE) {
              privTypeField.onChange(HashingAlgorithm.NONE)
              privKeyField.onChange("")
              authKeyField.onChange("")
            }
          }}
        />
        {hasAuth && (
          <VaultableInput
            required
            allowUnchanged={allowUnchanged}
            fieldType={FormControlType.Password}
            label={t("network.authKeySecret")}
            placeholder={t("common.eG", { example: t("credential.secretKey") })}
            control={control}
            setValue={setValue}
            name={`${namePrefix}.authKey`}
            vaultInstances={vaultInstances}
          />
        )}
        <Select
          required
          disabled={!hasAuth}
          fieldProps={{ w: hasPriv ? "50%" : "full" }}
          control={control}
          name={`${namePrefix}.privType`}
          options={privateKeyTypeOptions.options}
          label={t("credential.privType")}
          onSelectItem={(value) => {
            if (value === HashingAlgorithm.NONE) {
              privKeyField.onChange("")
            }
          }}
        />
        {hasPriv && (
          <VaultableInput
            required
            allowUnchanged={allowUnchanged}
            fieldType={FormControlType.Password}
            label={t("network.privKeySecret")}
            placeholder={t("common.eG", { example: t("credential.secretKey") })}
            control={control}
            setValue={setValue}
            name={`${namePrefix}.privKey`}
            vaultInstances={vaultInstances}
          />
        )}
      </>
    )
  }
  return null
}

const CREDENTIAL_MODE_OPTIONS = [
  { label: "device.accessCredentialsNone", description: "device.accessCredentialsNoneDescription", value: "none" },
  { label: "device.accessCredentialsGlobal", description: "device.accessCredentialsGlobalDescription", value: "global" },
  { label: "device.accessCredentialsSpecific", description: "device.accessCredentialsSpecificDescription", value: "specific" },
] as const

const SSH_AUTH_METHOD_OPTIONS = [
  { label: "auth.password", value: "password" },
  { label: "network.sshPrivateKey", value: "key" },
] as const

const SSH_HOST_KEY_VERIFICATION_OPTIONS = [
  {
    label: "device.sshHostKeyVerificationTrustAny",
    description: "device.sshHostKeyVerificationTrustAnyDescription",
    value: SshHostKeyVerification.TrustAny,
  },
  {
    label: "device.sshHostKeyVerificationTrustKnown",
    description: "device.sshHostKeyVerificationTrustKnownDescription",
    value: SshHostKeyVerification.TrustKnown,
  },
] as const

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

/** Formats an overridden address/port pair for the row's collapsed summary, e.g. "10.216.5.3:2222", "10.216.5.3", ":2222". */
function formatConnectionOverride(address: string, port: string): string {
  if (address && port) {
    return `${address}:${port}`
  }
  return address || (port ? `:${port}` : "")
}

function AccessRowSummary({
  mode,
  globalCredentialSetId,
  globalCredentialSetName,
  overrideConnection,
  address,
  port,
}: {
  mode: DeviceAccessCredentialMode
  globalCredentialSetId?: string
  globalCredentialSetName?: string
  overrideConnection: boolean
  address: string
  port: string
}) {
  const { t } = useTranslation()
  const credentialsSummary = (() => {
    if (mode === "none") {
      return t("device.accessCredentialsNone")
    }
    if (mode === "specific") {
      return t("device.accessCredentialsSpecific")
    }
    // mode === "global"
    if (!globalCredentialSetId || globalCredentialSetId === TRY_ALL_CREDENTIALS_VALUE) {
      return t("device.tryAllKnownCredentials")
    }
    return globalCredentialSetName || t("device.accessCredentialsGlobal")
  })()

  const connectionOverrideSummary = mode !== "none" && overrideConnection
    ? formatConnectionOverride(address, port)
    : ""

  return connectionOverrideSummary
    ? `${credentialsSummary} · ${connectionOverrideSummary}`
    : credentialsSummary
}

function AccessRow({
  control,
  setValue,
  index,
  value,
  vaultInstances,
  bulk = false,
}: {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  control: Control<any>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  setValue: UseFormSetValue<any>
  index: number
  value: DeviceAccessFormValue
  vaultInstances: Option<number>[]
  bulk?: boolean
}) {
  const { t } = useTranslation()
  const { data: credentialSets, isPending: isLoadingCredentialSets } = useCredentialSets()

  const { field: lockedField } = useController({ control, name: `accesses.${index}.locked` })
  const locked = bulk && Boolean(lockedField.value)

  const mode = useWatch({ control, name: `accesses.${index}.mode` }) as DeviceAccessCredentialMode
  const overrideConnection = useWatch({ control, name: `accesses.${index}.overrideConnection` }) as boolean
  const address = useWatch({ control, name: `accesses.${index}.address` }) as string
  const port = useWatch({ control, name: `accesses.${index}.port` }) as string
  const globalCredentialSetId = useWatch({ control, name: `accesses.${index}.globalCredentialSetId` }) as string
  const sshAuthMethod = useWatch({ control, name: `accesses.${index}.sshAuthMethod` }) as SshAuthMethod
  const sshHostKeyVerification = useWatch({
    control, name: `accesses.${index}.sshHostKeyVerification`,
  }) as SshHostKeyVerification
  const httpsCaTrustMode = useWatch({ control, name: `accesses.${index}.httpsCaTrustMode` }) as HttpsCaTrustMode

  // Captured once when the row is created: true only if this access already had a stored
  // secret to preserve (edit mode), so the lock-icon "unchanged" UX only appears then.
  const [allowUnchanged] = useState(
    () => value.password === null || value.privateKey === null || value.superPassword === null
      || value.authKey === null || value.privKey === null || value.community === null
  )

  const compatibleTypes = useMemo(() => getCompatibleCredentialTypes(value.protocol), [value.protocol])
  const effectiveSpecificType = getEffectiveSpecificCredentialType(value.protocol, sshAuthMethod)
  const isSsh = value.protocol === DeviceTypeProtocol.Ssh
  const isHttps = value.protocol === DeviceTypeProtocol.Https

  const globalCredentialSetOptions = useMemo(
    () => [
      {
        label: t("device.tryAllKnownCredentials"),
        description: t("device.tryAllKnownCredentialsDescription"),
        value: TRY_ALL_CREDENTIALS_VALUE,
      },
      ...(credentialSets ?? [])
        .filter((cs) => cs.type && compatibleTypes.includes(cs.type))
        .map((cs) => ({ label: cs.name ?? String(cs.id), value: String(cs.id) })),
    ],
    [credentialSets, compatibleTypes, t]
  )

  const globalCredentialSetName = useMemo(
    () => (credentialSets ?? []).find((cs) => String(cs.id) === globalCredentialSetId)?.name ?? undefined,
    [credentialSets, globalCredentialSetId]
  )

  const [isOpen, setIsOpen] = useState(false)

  const toggleLock = () => {
    lockedField.onChange(!locked)
    if (locked) setIsOpen(true)
  }

  // Only relevant to the (non-bulk) single-edit layout below - bulk rows show
  // no summary text at all, just the disclosure trigger and the lock toggle.
  const rowSummary = !isOpen && (
    <Text fontSize="sm" color="fg.muted">
      <AccessRowSummary
        mode={mode}
        globalCredentialSetId={globalCredentialSetId}
        globalCredentialSetName={globalCredentialSetName}
        overrideConnection={overrideConnection}
        address={address}
        port={port}
      />
    </Text>
  )

  return (
    <Collapsible.Root open={!locked && isOpen} onOpenChange={(details) => setIsOpen(details.open)}>
      {bulk ? (
        // Bulk mode: no summary/"unchanged" text, just the disclosure trigger
        // (chevron+name) and the right-aligned pen/pen-off toggle - consistent
        // with the domain/comments lock toggles.
        <Stack direction="row" alignItems="center" justifyContent="space-between" gap="2" w="full">
          <Collapsible.Trigger
            cursor={locked ? "default" : "pointer"}
            paddingY="3"
            display="flex"
            gap="2"
            alignItems="center"
            fontWeight="medium"
            disabled={locked}
          >
            {!locked && (
              <Collapsible.Indicator transition="transform 0.2s" _open={{ transform: "rotate(90deg)" }}>
                <LuChevronRight />
              </Collapsible.Indicator>
            )}
            <Text fontSize="sm" fontWeight="medium">{humanizeAccessName(value.accessName)}</Text>
          </Collapsible.Trigger>
          <BulkEditLockToggle locked={locked} onToggle={toggleLock} />
        </Stack>
      ) : (
        <Collapsible.Trigger
          cursor="pointer"
          paddingY="3"
          display="flex"
          gap="2"
          alignItems="center"
          fontWeight="medium"
          w="full"
        >
          <Collapsible.Indicator transition="transform 0.2s" _open={{ transform: "rotate(90deg)" }}>
            <LuChevronRight />
          </Collapsible.Indicator>
          <Stack direction="row" justifyContent="space-between" alignItems="center" flex="1">
            <Text fontSize="sm" fontWeight="medium">{humanizeAccessName(value.accessName)}</Text>
            {rowSummary}
          </Stack>
        </Collapsible.Trigger>
      )}
      <Collapsible.Content>
        <Stack direction="column" gap="4" px="4" pb="3">
          <Select
            label={t("device.accessCredentials")}
            control={control}
            name={`accesses.${index}.mode`}
            options={CREDENTIAL_MODE_OPTIONS.map((option) => ({
              label: t(option.label),
              description: t(option.description),
              value: option.value,
            }))}
          />
          {mode !== "none" && (
            <>
              {mode === "global" && (
                <Select
                  required
                  isLoading={isLoadingCredentialSets}
                  label={t("device.selectCredentialSet")}
                  placeholder={t("device.selectCredentialSet")}
                  control={control}
                  name={`accesses.${index}.globalCredentialSetId`}
                  options={globalCredentialSetOptions}
                />
              )}
              {mode === "specific" && (
                <>
                  {isSsh && (
                    <Select
                      label={t("device.sshAuthMethod")}
                      control={control}
                      name={`accesses.${index}.sshAuthMethod`}
                      options={SSH_AUTH_METHOD_OPTIONS.map((option) => ({ label: t(option.label), value: option.value }))}
                    />
                  )}
                  {effectiveSpecificType && (
                    <SpecificCredentialFields
                      control={control}
                      setValue={setValue}
                      namePrefix={`accesses.${index}`}
                      type={effectiveSpecificType}
                      allowUnchanged={allowUnchanged}
                      vaultInstances={vaultInstances}
                    />
                  )}
                </>
              )}
              <Switch
                control={control}
                name={`accesses.${index}.overrideConnection`}
                label={t("device.overrideConnection")}
                description={t("device.overrideConnectionSettings")}
              />
              {overrideConnection && (
                <Stack direction="row" gap="3" align="flex-end">
                  <FormControl
                    flex="3"
                    label={t("device.connectIp")}
                    placeholder={t("common.eG", { example: "10.216.5.3" })}
                    control={control}
                    name={`accesses.${index}.address`}
                  />
                  <FormControl
                    flex="1"
                    type={FormControlType.Number}
                    label={t("network.port")}
                    placeholder={String(value.defaultPort)}
                    control={control}
                    name={`accesses.${index}.port`}
                    min={1}
                    max={65535}
                    rules={{
                      min: { value: 1, message: t("network.invalidPort") },
                      max: { value: 65535, message: t("network.invalidPort") },
                    }}
                  />
                </Stack>
              )}
              {isSsh && (
                <Stack direction="column" gap="3">
                  <Select
                    label={t("device.sshHostKeyVerification")}
                    control={control}
                    name={`accesses.${index}.sshHostKeyVerification`}
                    options={SSH_HOST_KEY_VERIFICATION_OPTIONS.map((option) => ({
                      label: t(option.label),
                      description: t(option.description),
                      value: option.value,
                    }))}
                  />
                  {sshHostKeyVerification !== SshHostKeyVerification.TrustAny && (
                    <Stack gap="1">
                      <FormControl
                        autosize
                        mono
                        clearable
                        rows={3}
                        type={FormControlType.LongText}
                        label={t("device.sshTrustedHostKeys")}
                        placeholder={t("device.sshTrustedHostKeysPlaceholder")}
                        control={control}
                        name={`accesses.${index}.sshTrustedHostKeys`}
                      />
                      <Text fontSize="xs" color="fg.muted">{t("device.sshTrustedHostKeysHelp")}</Text>
                    </Stack>
                  )}
                </Stack>
              )}
              {isHttps && (
                <Stack direction="column" gap="3">
                  <Select
                    label={t("device.httpsCaTrustMode")}
                    control={control}
                    name={`accesses.${index}.httpsCaTrustMode`}
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
                      rows={4}
                      type={FormControlType.LongText}
                      label={t("device.httpsCustomCaCertificate")}
                      placeholder={t("device.httpsCustomCaCertificatePlaceholder")}
                      control={control}
                      name={`accesses.${index}.httpsCustomCaCertificate`}
                    />
                  )}
                </Stack>
              )}
            </>
          )}
        </Stack>
      </Collapsible.Content>
    </Collapsible.Root>
  )
}

/** Falls back to "" only for a genuinely absent value, preserving the PASSWORD_UNCHANGED (null) sentinel as-is. */
function preserveSecret(value: string | null | undefined): string | null {
  return value === undefined ? "" : value
}

/**
 * Renders one collapsible row per access declared by the selected device
 * driver (e.g. "ssh", "telnet", or any other access a custom driver might
 * declare, such as an HTTP access): an optional address/port override, plus
 * a per-access credential choice (none/disabled / a global account - either
 * a specific pick or "try all known credentials" / a specific account
 * defined just for this access). Backed by an `accesses` field array on the
 * form, kept in sync with the driver's access definitions.
 */
export default function DeviceAccessFields({ control, setValue, accessDefinitions, bulk = false }: DeviceAccessFieldsProps) {
  const { t } = useTranslation()
  const { fields, replace } = useFieldArray({ control, name: "accesses" })
  const { data: vaultInstanceList } = useVaultInstanceChoices()

  const vaultInstances = useMemo(
    () => (vaultInstanceList ?? []).map((instance) => ({ label: instance.name ?? String(instance.id), value: instance.id as number })),
    [vaultInstanceList]
  )

  const accessNamesKey = (accessDefinitions ?? []).map((def) => def.name).sort().join(",")

  useEffect(() => {
    const defs = accessDefinitions ?? []
    replace(
      defs.map((def) => {
        const existing = (fields as unknown as DeviceAccessFormValue[]).find((f) => f.accessName === def.name)
        return {
          accessName: def.name,
          protocol: def.protocol,
          defaultPort: def.defaultPort,
          // Bulk edit: every row starts locked ("leave unchanged") until the user
          // explicitly unlocks it - see `AccessRow`'s lock toggle. Not used outside bulk mode.
          locked: existing?.locked ?? bulk,
          overrideConnection: existing?.overrideConnection ?? false,
          address: existing?.address ?? "",
          port: existing?.port ?? "",
          mode: existing?.mode ?? "global",
          globalCredentialSetId: existing?.globalCredentialSetId ?? TRY_ALL_CREDENTIALS_VALUE,
          sshAuthMethod: existing?.sshAuthMethod ?? "password",
          username: existing?.username ?? "",
          // `existing?.field ?? ""` would collapse PASSWORD_UNCHANGED (null) into
          // "", losing the lock-icon "unchanged" state on every sync - `null` must
          // be preserved as-is, only actual `undefined` (no existing row) falls to "".
          password: preserveSecret(existing?.password),
          superPassword: preserveSecret(existing?.superPassword),
          privateKey: preserveSecret(existing?.privateKey),
          community: preserveSecret(existing?.community),
          authType: existing?.authType,
          authKey: preserveSecret(existing?.authKey),
          privType: existing?.privType,
          privKey: preserveSecret(existing?.privKey),
          sshHostKeyVerification: existing?.sshHostKeyVerification ?? SshHostKeyVerification.TrustAny,
          sshTrustedHostKeys: existing?.sshTrustedHostKeys ?? "",
          httpsCaTrustMode: existing?.httpsCaTrustMode ?? HttpsCaTrustMode.SystemTruststore,
          httpsCustomCaCertificate: existing?.httpsCustomCaCertificate ?? "",
          usernameVaultInstanceId: existing?.usernameVaultInstanceId ?? null,
          usernameVaultPath: existing?.usernameVaultPath ?? "",
          passwordVaultInstanceId: existing?.passwordVaultInstanceId ?? null,
          passwordVaultPath: existing?.passwordVaultPath ?? "",
          superPasswordVaultInstanceId: existing?.superPasswordVaultInstanceId ?? null,
          superPasswordVaultPath: existing?.superPasswordVaultPath ?? "",
          privateKeyVaultInstanceId: existing?.privateKeyVaultInstanceId ?? null,
          privateKeyVaultPath: existing?.privateKeyVaultPath ?? "",
          communityVaultInstanceId: existing?.communityVaultInstanceId ?? null,
          communityVaultPath: existing?.communityVaultPath ?? "",
          authKeyVaultInstanceId: existing?.authKeyVaultInstanceId ?? null,
          authKeyVaultPath: existing?.authKeyVaultPath ?? "",
          privKeyVaultInstanceId: existing?.privKeyVaultInstanceId ?? null,
          privKeyVaultPath: existing?.privKeyVaultPath ?? "",
        }
      })
    )
    // Only re-sync when the set of accesses actually changes (e.g. device type
    // switched), not on every render - otherwise in-progress input gets wiped.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [accessNamesKey])

  if (!accessDefinitions || accessDefinitions.length === 0) {
    return null
  }

  // Group definitions in first-seen order, then sort each group's rows by
  // priority (descending) - mirrors the backend's own group/priority model.
  const indexByName = new Map(
    (fields as unknown as DeviceAccessFormValue[]).map((f, index) => [f.accessName, index])
  )
  const groupOrder: string[] = []
  const defsByGroup = new Map<string, DeviceAccessDefinition[]>()
  accessDefinitions.forEach((def) => {
    if (!defsByGroup.has(def.group)) {
      defsByGroup.set(def.group, [])
      groupOrder.push(def.group)
    }
    defsByGroup.get(def.group)?.push(def)
  })

  return (
    <Stack gap="5">
      {groupOrder.map((group) => {
        const groupLabel = t(`device.accessGroup${group.charAt(0).toUpperCase()}${group.slice(1)}`, {
          defaultValue: humanizeAccessName(group),
        })
        return (
          <Stack key={group} gap="1">
            <Text fontWeight="medium">
              {t("device.connectionOverridesPerAccess")} - {groupLabel}
            </Text>
            <Stack gap="0" separator={<Separator />}>
              {(defsByGroup.get(group) ?? [])
                .slice()
                .sort((a, b) => b.priority - a.priority)
                .map((def) => {
                  const index = indexByName.get(def.name)
                  if (index === undefined) {
                    return null
                  }
                  return (
                    <AccessRow
                      key={fields[index].id}
                      control={control}
                      setValue={setValue}
                      index={index}
                      value={fields[index] as unknown as DeviceAccessFormValue}
                      vaultInstances={vaultInstances}
                      bulk={bulk}
                    />
                  )
                })}
            </Stack>
          </Stack>
        )
      })}
    </Stack>
  )
}

/** Passes a secret field through, unless it's empty or the "unchanged" sentinel (`PASSWORD_UNCHANGED` = null) from an edit form - in which case it's omitted, preserving the existing stored value. */
function secret(value: string | null | undefined): string | undefined {
  return value || undefined
}

/**
 * A secret of a request: its Vault location if Vault-backed, else its new value (the other one is set to null
 * to switch between them) or nothing (unchanged).
 */
function secretFields<K extends string>(
  name: K,
  value: string | null | undefined,
  instanceId: number | null | undefined,
  path: string | null | undefined
) {
  const vault = vaultOf(instanceId, path)
  const fields: Record<string, unknown> = {}
  if (vault) {
    fields[`${name}Vault`] = vault
    fields[name] = null
  } else {
    fields[`${name}Vault`] = null
    const newValue = secret(value)
    if (newValue !== undefined) {
      fields[name] = newValue
    }
  }
  return fields
}

function buildSpecificCredentialPayload(access: DeviceAccessFormValue): CredentialSet | undefined {
  const type = getEffectiveSpecificCredentialType(access.protocol, access.sshAuthMethod)
  if (!type) {
    return undefined
  }
  const username = secretFields("username", access.username, access.usernameVaultInstanceId, access.usernameVaultPath)
  const password = secretFields("password", access.password, access.passwordVaultInstanceId, access.passwordVaultPath)
  const superPassword = secretFields("superPassword", access.superPassword, access.superPasswordVaultInstanceId, access.superPasswordVaultPath)
  switch (type) {
    case "SSH":
    case "TELNET":
      return { type, ...username, ...password, ...superPassword } as CredentialSet
    case "SSH_KEY":
      return {
        type,
        ...username,
        ...secretFields("privateKey", access.privateKey, access.privateKeyVaultInstanceId, access.privateKeyVaultPath),
        ...password,
        ...superPassword,
      } as CredentialSet
    case "HTTP":
      return { type, ...username, ...password } as CredentialSet
    case "SNMP_V1":
    case "SNMP_V2C":
      return {
        type,
        ...secretFields("community", access.community, access.communityVaultInstanceId, access.communityVaultPath),
      } as CredentialSet
    case "SNMP_V3":
      return {
        type,
        ...username,
        authType: access.authType,
        ...secretFields("authKey", access.authKey, access.authKeyVaultInstanceId, access.authKeyVaultPath),
        privType: access.privType,
        ...secretFields("privKey", access.privKey, access.privKeyVaultInstanceId, access.privKeyVaultPath),
      } as CredentialSet
    default:
      return undefined
  }
}

/**
 * Shapes the `accesses` field array of a device create/edit form into the
 * accesses of the request. An access with no row at all is never used (see
 * `AccessManager`), so a `none` row is simply omitted here entirely - the
 * server removes any existing row for it. A `global` mode with a specific
 * pick gives the ID of the credential set, a `global` mode left on "try all
 * known credentials" is sent as a bare `{accessName}` (falls through to the
 * domain's auto-try pool), and a `specific` pin builds the inline credential
 * object for that access's family (honoring the SSH password-vs-key choice).
 */
export function buildAccessesPayload(
  accesses: DeviceAccessFormValue[] | undefined,
  credentialSets: Brief["CredentialSet"][] | null | undefined
): Schemas["DeviceAccessRequest"][] {
  const result: Schemas["DeviceAccessRequest"][] = []
  for (const access of accesses ?? []) {
    if (access.mode === "none") {
      continue
    }
    const entry: Schemas["DeviceAccessRequest"] = { accessName: access.accessName }
    if (access.overrideConnection && access.address) {
      entry.address = access.address
    }
    if (access.overrideConnection && access.port) {
      entry.port = Number(access.port)
    }
    if (access.protocol === DeviceTypeProtocol.Ssh) {
      entry.sshHostKeyVerification = access.sshHostKeyVerification
      entry.sshTrustedHostKeys = access.sshTrustedHostKeys || undefined
    }
    if (access.protocol === DeviceTypeProtocol.Https) {
      entry.httpsCaTrustMode = access.httpsCaTrustMode
      entry.httpsCustomCaCertificate = access.httpsCustomCaCertificate || undefined
    }
    if (access.mode === "global" && access.globalCredentialSetId
        && access.globalCredentialSetId !== TRY_ALL_CREDENTIALS_VALUE) {
      const credentialSet = (credentialSets ?? []).find((cs) => String(cs.id) === access.globalCredentialSetId)
      if (credentialSet?.id !== undefined) {
        entry.globalCredentialSet = credentialSet.id
      }
    }
    else if (access.mode === "specific") {
      const specific = buildSpecificCredentialPayload(access)
      if (specific) {
        entry.specificCredentialSet = specific
      }
    }
    result.push(entry)
  }
  return result
}
