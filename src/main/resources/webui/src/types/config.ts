import type { Schemas } from "@/api/client"
import { DeviceNetworkClass } from "./device"

export type ConfigNumericAttribute = Schemas["Numeric"]
export type ConfigTextAttribute = Schemas["Text"]
export type ConfigLongTextAttribute = Schemas["LongText"]
export type ConfigBinaryAttribute = Schemas["Binary"]
export type ConfigBinaryFileAttribute = Schemas["BinaryFile"]

export type ConfigAttribute =
  | ConfigNumericAttribute
  | ConfigTextAttribute
  | ConfigLongTextAttribute
  | ConfigBinaryAttribute
  | ConfigBinaryFileAttribute

/** A configuration of a device (the attributes are only there in the full view). */
export type Config = Omit<Schemas["Config"], "attributes"> & { id: number; changeDate: Date; attributes?: ConfigAttribute[] }

export type LightConfig = {
  id: number
  deviceName: string
  deviceId: number
  changeDate: number
  author: string
  deviceNetworkClass: DeviceNetworkClass
}
