import { createOptionHook } from "@/hooks"
import { HashingAlgorithm } from "@/types"
import { tKey } from "@/i18n/tKey"

export const useDeviceCredentialSetAuthTypeOptions = createOptionHook([
  {
    label: tKey("network.noAuthentication"),
    value: HashingAlgorithm.NONE,
  },
  {
    label: tKey("network.md5"),
    value: HashingAlgorithm.MD5,
  },
  {
    label: tKey("network.sha"),
    value: HashingAlgorithm.SHA,
  },
  {
    label: tKey("network.sha224"),
    value: HashingAlgorithm.HMAC128SHA224,
  },
  {
    label: tKey("network.sha256"),
    value: HashingAlgorithm.HMAC192SHA256,
  },
  {
    label: tKey("network.sha384"),
    value: HashingAlgorithm.HMAC256SHA384,
  },
  {
    label: tKey("network.sha512"),
    value: HashingAlgorithm.HMAC384SHA512,
  },
])
