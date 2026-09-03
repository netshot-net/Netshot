import { Address } from "./address"
import { CredentialSet } from "./credentialSet"
import { MgmtDomain } from "./mgmtDomain"

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

export type DeviceModule = {
  id: number
  slot: string
  partNumber: string
  serialNumber: string
  firstSeenDate: number
  lastSeenDate: number
  removed: boolean
}

export type DeviceOwnerGroup = {
  changeDate: number
  id: number
  name: string
  folder: string
  hiddenFromReports: boolean
  type: string
}

export type LightDevice = {
  id: number
  name: string
  family: string
  mgmtAddress: string
  status: DeviceStatus
  driver: string
  softwareLevel: DeviceSoftwareLevel
  configCompliant: boolean
  eos: boolean
  eol: boolean
  networkClass: DeviceNetworkClass
}

export type DeviceNumericAttribute = {
  type: string
  name: string
  number: number
}

export type DeviceTextAttribute = {
  type: string
  name: string
  text: string
}

export type DeviceBinaryAttribute = {
  type: string
  name: string
  assumption: boolean
}

export type DeviceAttribute = DeviceNumericAttribute | DeviceTextAttribute | DeviceBinaryAttribute

export enum SshHostKeyVerification {
  TrustAny = "TRUST_ANY",
  TrustKnown = "TRUST_KNOWN",
}

export enum HttpsCaTrustMode {
  TrustAny = "TRUST_ANY",
  SystemTruststore = "SYSTEM_TRUSTSTORE",
  CustomCa = "CUSTOM_CA",
}

export type DeviceAccess = {
  accessName: string
  address?: string
  port?: number
  globalCredentialSet?: CredentialSet
  specificCredentialSet?: CredentialSet
  sshHostKeyVerification?: SshHostKeyVerification
  sshTrustedHostKeys?: string
  sshTrustedHostKeysSince?: number
  httpsCaTrustMode?: HttpsCaTrustMode
  httpsCustomCaCertificate?: string
}

export type Device = {
  attributes: DeviceAttribute[]
  changeDate: number
  comments: string
  contact: string
  createdDate: number
  creator: string
  driver: string
  eolDate: number
  eolModule: DeviceModule
  eosDate: number
  eosModule: DeviceModule
  family: string
  id: number
  location: string
  mgmtAddress: string
  mgmtDomain: MgmtDomain
  name: string
  networkClass: DeviceNetworkClass
  ownerGroups: DeviceOwnerGroup[]
  serialNumber: string
  softwareLevel: DeviceSoftwareLevel
  softwareVersion: string
  status: DeviceStatus
  accesses: DeviceAccess[]
  options: Record<string, string | boolean>
  realDeviceType: string
  endOfLife: boolean
  endOfSale: boolean
  compliant: true
  lastConnectionDate: number
}

export enum DeviceComplianceResultType {
  Conforming = "CONFORMING",
  NonConforming = "NONCONFORMING",
  Disabled = "DISABLED",
  Exempted = "EXEMPTED",
  InvalidRule = "INVALIDRULE",
  NotApplication = "NOTAPPLICABLE",
}

export type DeviceComplianceResult = {
  ruleId: number
  ruleName: string
  policyId: number
  policyName: string
  result: DeviceComplianceResultType
  comment: string
  checkDate: number
  expirationDate: number
}

export type DeviceDiagnosticResult = {
  creationDate: number
  lastCheckDate: number
  diagnosticName: string
  diagnosticId: number
  type: DeviceAttributeType
  number?: number
  text?: string
  assumption?: boolean
}

export type DeviceInterface = {
  id: number
  interfaceName: string
  ip4Addresses: Address[]
  ip6Addresses: Address[]
  vrfInstance: string
  virtualDevice: string
  description: string
  enabled: boolean
  level3: boolean
  macAddress: string
}

export type DeviceFamily = {
  driver: string
  deviceFamily: string
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

export type DeviceType = {
  attributes: DeviceAttributeDefinition[]
  author: string
  cliMainModes: string[]
  description: string
  location: {
    type: string
    fileName: string
  }
  name: string
  priority: number
  protocols: DeviceTypeProtocol[]
  sourceHash: string
  accessDefinitions: Record<string, DeviceAccessDefinition>
  options: Record<string, DeviceOptionDefinition>
  version: string
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
