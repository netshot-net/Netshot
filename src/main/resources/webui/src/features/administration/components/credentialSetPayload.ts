import { CredentialSet, CredentialSetRequest } from "@/api/admin"
import { PASSWORD_UNCHANGED } from "@/components/FormControl"
import { CredentialSetType } from "@/types"
import { DeviceCredentialSetForm } from "./DeviceCredentialSetForm"

type SecretField = "username" | "password" | "superPassword" | "privateKey" | "community" | "authKey" | "privKey"

/** The properties each type of credential set accepts (the API refuses the others). */
const FIELDS: Record<CredentialSetType, SecretField[]> = {
  [CredentialSetType.GLOBAL]: [],
  [CredentialSetType.SSH]: ["username", "password", "superPassword"],
  [CredentialSetType.Telnet]: ["username", "password", "superPassword"],
  [CredentialSetType.SSHKey]: ["username", "password", "superPassword", "privateKey"],
  [CredentialSetType.HTTP]: ["username", "password"],
  [CredentialSetType.SNMP_V1]: ["community"],
  [CredentialSetType.SNMP_V2C]: ["community"],
  [CredentialSetType.SNMP_V3]: ["username", "authKey", "privKey"],
}

/** The flag of the credential set telling whether the (write-only) secret has a stored value. */
const HAS_FLAGS = {
  password: "hasPassword",
  superPassword: "hasSuperPassword",
  privateKey: "hasPrivateKey",
  community: "hasCommunity",
  authKey: "hasAuthKey",
  privKey: "hasPrivKey",
} as const

/**
 * Builds the request from the form.
 *
 * A Vault-backed field gives its location only; a local one its value. In an update, a secret left unchanged
 * is left out (the server keeps it), and an emptied one is sent as null (cleared). The fields which don't
 * apply to the type are left out.
 */
export function buildCredentialSetPayload(
  values: DeviceCredentialSetForm,
  mode: "create" | "update"
): CredentialSetRequest {
  const payload: Record<string, unknown> = {
    name: values.name,
    type: values.type,
  }

  if (values.mgmtDomain) {
    payload.mgmtDomain = Number(values.mgmtDomain)
  }

  if (values.type === CredentialSetType.SNMP_V3) {
    payload.authType = values.authType
    payload.privType = values.privType
  }

  const form = values as Record<string, unknown>
  for (const field of FIELDS[values.type] ?? []) {
    const vaultInstanceId = form[`${field}VaultInstanceId`]
    if (vaultInstanceId !== null && vaultInstanceId !== undefined) {
      payload[`${field}Vault`] = {
        vaultInstance: Number(vaultInstanceId),
        path: form[`${field}VaultPath`] ?? "",
      }
      continue
    }
    const value = form[field]
    if (value === PASSWORD_UNCHANGED && field !== "username") {
      continue
    }
    if (value === undefined || value === null || value === "") {
      if (mode === "update") {
        payload[field] = null
      }
      continue
    }
    payload[field] = value
  }

  return payload as unknown as CredentialSetRequest
}

/** Gets the values of the form from a credential set (the secrets are "unchanged" when they are set). */
export function credentialSetFormValues(credentialSet: CredentialSet): Partial<DeviceCredentialSetForm> {
  const values: Record<string, unknown> = {
    name: credentialSet.name,
    mgmtDomain: credentialSet.mgmtDomain?.id ?? null,
    type: credentialSet.type,
  }

  const source = credentialSet as Record<string, unknown>
  for (const field of FIELDS[credentialSet.type as CredentialSetType] ?? []) {
    const vault = source[`${field}Vault`] as CredentialSet["usernameVault"]
    values[`${field}VaultInstanceId`] = vault?.vaultInstance?.id ?? null
    values[`${field}VaultPath`] = vault?.path ?? ""
    if (field === "username") {
      values.username = credentialSet.username ?? ""
    } else {
      const hasValue = vault !== undefined && vault !== null ? true : source[HAS_FLAGS[field]] === true
      values[field] = hasValue ? PASSWORD_UNCHANGED : ""
    }
  }

  if (credentialSet.type === "SNMP_V3") {
    values.authType = credentialSet.authType
    values.privType = credentialSet.privType
  }

  return values as Partial<DeviceCredentialSetForm>
}
