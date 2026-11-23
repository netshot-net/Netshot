import { createOptionHook } from "@/hooks"
import { VaultInstanceType } from "@/types"
import { tKey } from "@/i18n/tKey"

export const useVaultInstanceTypeOptions = createOptionHook([
  {
    label: tKey("vault.typeVaultKv2"),
    value: VaultInstanceType.HashicorpKv2,
  },
])
