import { createOptionHook } from "@/hooks"
import { VaultAuthMethod } from "@/types"
import { tKey } from "@/i18n/tKey"

export const useVaultAuthMethodOptions = createOptionHook([
  {
    label: tKey("vault.authMethodAppRole"),
    value: VaultAuthMethod.AppRole,
  },
  {
    label: tKey("vault.authMethodJwt"),
    value: VaultAuthMethod.Jwt,
  },
])
