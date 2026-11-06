import { DeviceSoftwareLevel, DeviceType } from "@/types"

export type RuleForm = {
  name: string
  enabled: boolean
  script: string
  text: string
  regExp: boolean
  context: string
  driver: DeviceType["name"] | null
  field: string | null
  anyBlock: string
  matchAll: boolean
  invert: string
  normalize: boolean
}

export type SoftwareRuleFormValues = {
  driver: DeviceType["name"] | null
  family: string
  familyRegExp: boolean
  group: number | null
  level: DeviceSoftwareLevel
  partNumber: string
  partNumberRegExp: boolean
  softwareVersion: string
  softwareVersionRegExp: boolean
}

export type HardwareRuleFormValues = {
  driver: DeviceType["name"] | null
  endOfLife: number | undefined
  endOfSale: number | undefined
  family: string
  familyRegExp: boolean
  partNumber: string
  partNumberRegExp: boolean
  group: number | null
}
