import { createOptionHook } from "@/hooks"
import { CredentialSetType } from "@/types"
import { tKey } from "@/i18n/tKey"

export const useDeviceCredentialSetTypeOptions = createOptionHook([
  {
    label: tKey("network.snmpV1"),
    value: CredentialSetType.SNMP_V1,
  },
  {
    label: tKey("network.snmpV2c"),
    value: CredentialSetType.SNMP_V2C,
  },
  {
    label: tKey("network.snmpV3"),
    value: CredentialSetType.SNMP_V3,
  },
  {
    label: tKey("network.ssh"),
    value: CredentialSetType.SSH,
  },
  {
    label: tKey("network.sshKey"),
    value: CredentialSetType.SSHKey,
  },
  {
    label: tKey("network.telnet"),
    value: CredentialSetType.Telnet,
  },
  {
    label: tKey("network.http"),
    value: CredentialSetType.HTTP,
  },
])
