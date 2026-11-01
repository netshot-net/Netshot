import { HashingAlgorithm } from "./hashingAlgorithm"
import { MgmtDomain } from "./mgmtDomain"

export enum CredentialSetType {
  GLOBAL = "global",
  SNMP_V1 = "SNMP_V1",
  SNMP_V2C = "SNMP_V2C",
  SNMP_V3 = "SNMP_V3",
  SSH = "SSH",
  SSHKey = "SSH_KEY",
  Telnet = "Telnet",
  HTTP = "HTTP",
}

export type CredentialSet = {
  id: number
  name: string
  mgmtDomain: MgmtDomain
  deviceSpecific: boolean
  type: CredentialSetType
  password: string
  superPassword: string
  username: string
  privateKey: string
  community: string
  privType: HashingAlgorithm
  authType: HashingAlgorithm
  privKey: string
  authKey: string
} & VaultableFieldRefs

// Each of these fields can independently be Local (siblings all null) or
// Vault-backed (vaultInstanceId set, path identifies the secret in Vault -
// the part after the last "/" is the key within it).
export type VaultableFieldRefs = {
  usernameVaultInstanceId?: number | null
  usernameVaultPath?: string | null
  passwordVaultInstanceId?: number | null
  passwordVaultPath?: string | null
  superPasswordVaultInstanceId?: number | null
  superPasswordVaultPath?: string | null
  privateKeyVaultInstanceId?: number | null
  privateKeyVaultPath?: string | null
  communityVaultInstanceId?: number | null
  communityVaultPath?: string | null
  authKeyVaultInstanceId?: number | null
  authKeyVaultPath?: string | null
  privKeyVaultInstanceId?: number | null
  privKeyVaultPath?: string | null
}
