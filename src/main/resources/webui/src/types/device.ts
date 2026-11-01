import type { Schemas } from "@/api/client"

export enum DeviceStatus {
  Production = "INPRODUCTION",
  Disabled = "DISABLED",
  PreProduction = "PREPRODUCTION",
}

export enum DeviceSoftwareLevel {
  GOLD = "GOLD",
  SILVER = "SILVER",
  BRONZE = "BRONZE",
  NON_COMPLIANT = "NON-COMPLIANT",
  UNKNOWN = "UNKNOWN",
}

export enum DeviceNetworkClass {
  Firewall = "FIREWALL",
  LoadBalancer = "LOADBALANCER",
  Router = "ROUTER",
  Server = "SERVER",
  Switch = "SWITCH",
  SwitchRouter = "SWITCHROUTER",
  AccessPoint = "ACCESSPOINT",
  WirelessController = "WIRELESSCONTROLLER",
  ConsoleServer = "CONSOLESERVER",
  Unknown = "UNKNOWN",
  VoiceGateway = "VOICEGATEWAY",
}

export enum DeviceAddressUsage {
  Primary = "Primary",
}

export type DeviceOwnerGroup = {
  changeDate: number
  id: number
  name: string
  folder: string
  hiddenFromReports: boolean
  type: string
}

export type DeviceAttribute = Schemas["DeviceAttribute"]

export enum SshHostKeyVerification {
  TrustAny = "TRUST_ANY",
  TrustKnown = "TRUST_KNOWN",
}

export enum HttpsCaTrustMode {
  TrustAny = "TRUST_ANY",
  SystemTruststore = "SYSTEM_TRUSTSTORE",
  CustomCa = "CUSTOM_CA",
}

export enum DeviceComplianceResultType {
  Conforming = "CONFORMING",
  NonConforming = "NONCONFORMING",
  Disabled = "DISABLED",
  Exempted = "EXEMPTED",
  InvalidRule = "INVALIDRULE",
  NotApplication = "NOTAPPLICABLE",
}

export enum DeviceAttributeType {
  Numeric = "NUMERIC",
  Text = "TEXT",
  LongText = "LONGTEXT",
  Date = "DATE",
  Binary = "BINARY",
  BinaryFile = "BINARYFILE",
}

export enum DeviceAttributeLevel {
  Device = "DEVICE",
  Config = "CONFIG",
}

export type DeviceAttributeDefinition = {
  name: string
  checkable: boolean
  comparable: boolean
  dump: boolean
  level: DeviceAttributeLevel
  postDump: string
  postLineDump: string
  preDump: string
  preLineDump: string
  searchable: boolean
  title: string
  type: DeviceAttributeType
}

export enum DeviceTypeProtocol {
  Ssh = "SSH",
  Telnet = "TELNET",
  SnmpV1 = "SNMPV1",
  SnmpV2c = "SNMPV2C",
  SnmpV3 = "SNMPV3",
  Http = "HTTP",
  Https = "HTTPS",
}

export type DeviceAccessDefinition = {
  name: string
  protocol: DeviceTypeProtocol
  description?: string
  defaultPort: number
  group: string
  priority: number
}

export enum DriverOptionType {
  Text = "TEXT",
  Boolean = "BOOLEAN",
}

export type DeviceOptionDefinition = {
  name: string
  title: string
  type: DriverOptionType
  choices?: string[]
  defaultValue?: string | boolean
}

export type DeviceAccessFailure = {
  driver: string
  family: string
  id: number
  lastFailure: number
  lastSuccess: number
  mgmtAddress: string
  name: string
  networkClass: DeviceNetworkClass
  status: DeviceStatus
}

/** A device as the API gives it (a list gives the summary properties only, unless more are asked for). */
export type Device = Omit<Schemas["Device"], "status" | "networkClass" | "softwareLevel"> & {
  id: number
  name: string
  mgmtAddress: string
  // The enums of the UI have the values of the API
  status: DeviceStatus
  networkClass: DeviceNetworkClass
  softwareLevel: DeviceSoftwareLevel
}

export type DeviceModule = Schemas["DeviceModule"] & { id: number }
export type DeviceInterface = Schemas["DeviceInterface"] & { id: number }
export type DeviceAccess = Schemas["DeviceAccess"]
export type DeviceFamily = Schemas["DeviceFamily"]
export type DeviceTag = Schemas["DeviceTag"]
export type DeviceComplianceResult = Schemas["DeviceComplianceResult"]

/** The loaded drivers: the enums of the UI have the values of the API. */
export type DeviceType = Omit<Schemas["DeviceType"], "attributes" | "accesses" | "options" | "protocols"> & {
  name: string
  description: string
  protocols: DeviceTypeProtocol[]
  attributes: DeviceAttributeDefinition[]
  accesses: DeviceAccessDefinition[]
  options: DeviceOptionDefinition[]
}
