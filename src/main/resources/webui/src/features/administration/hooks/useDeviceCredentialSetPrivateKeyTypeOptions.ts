import { createOptionHook } from "@/hooks"
import { HashingAlgorithm } from "@/types"
import { tKey } from "@/i18n/tKey"

export const useDeviceCredentialSetPrivateKeyTypeOptions = createOptionHook([
  {
    label: tKey("network.noPrivacy"),
    value: HashingAlgorithm.NONE,
  },
  {
    label: tKey("network.des"),
    value: HashingAlgorithm.DES,
  },
  {
    label: tKey("network.des3"),
    value: HashingAlgorithm.DES3,
  },
  {
    label: tKey("network.aes128"),
    value: HashingAlgorithm.AES128,
  },
  {
    label: tKey("network.aes192"),
    value: HashingAlgorithm.AES192,
  },
  {
    label: tKey("network.aes256"),
    value: HashingAlgorithm.AES256,
  },
])
