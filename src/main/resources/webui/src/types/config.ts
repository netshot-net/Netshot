import { DeviceNetworkClass } from "./device"

export type ConfigNumericAttribute = {
  type: string
  name: string
  number: number
}

export type ConfigTextAttribute = {
  type: string
  name: string
  text: string
}

export type ConfigLongTextAttribute = {
  type: string
  name: string
}

export type ConfigBinaryAttribute = {
  type: string
  name: string
  assumption: boolean
}

export type ConfigBinaryFileAttribute = {
  type: string
  name: string
  originalName: string
  fileSize: number
  checksum: string
}

export type ConfigAttribute =
  | ConfigNumericAttribute
  | ConfigTextAttribute
  | ConfigLongTextAttribute
  | ConfigBinaryAttribute
  | ConfigBinaryFileAttribute

export type Config = {
  id: number
  changeDate: number
  author: string
  attributes: ConfigAttribute[]
  customHash: string
}

export type LightConfig = {
  id: number
  deviceName: string
  deviceId: number
  changeDate: number
  author: string
  deviceNetworkClass: DeviceNetworkClass
}

export enum ConfigDiffType {
  Change = "CHANGE",
}

export type ConfigDiffHierarchy = {
  line: string
  position: number
}

export type ConfigDiffProp = {
  item: string
  diffType: ConfigDiffType
  originalPosition: number
  revisedPosition: number
  originalLines: string[]
  revisedLines: string[]
  preContext: string[]
  postContext: string[]
  hierarchy: ConfigDiffHierarchy[]
}

export type ConfigDiff = {
  originalDate: number
  revisedDate: number
  originalConfigId: number
  revisedConfigId: number
  originalConfig?: Config
  revisedConfig?: Config
  deltas: {
    additionalProp1: ConfigDiffProp[]
    additionalProp2: ConfigDiffProp[]
    additionalProp3: ConfigDiffProp[]
  }
}
