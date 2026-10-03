import { DeviceType, Option } from "@/types"

export enum AttributeGroupType {
  Generic = "generic",
  TypeSpecific = "type-specific",
  ComplianceRuleResult = "compliance-rule-result",
  DiagnosticResult = "diagnostic-result",
}

export type AttributeOption = Option<{
  name: string
  type: string
  choices?: Option<string | number>[]
}>

export enum AttributeType {
  Date = "DATE",
  Text = "TEXT",
  LongText = "LONGTEXT",
  Enum = "ENUM",
  Numeric = "NUMERIC",
  IpAddress = "IPADDRESS",
  MacAddress = "MACADDRESS",
  Id = "id",
  Binary = "BINARY",
  /** Free text, with the existing values (choices) suggested */
  Tag = "TAG",
}

export enum OperatorType {
  Is = "IS",
  In = "IN",
  Contains = "CONTAINS",
  ContainsNoCase = "CONTAINSNOCASE",
  StartsWith = "STARTSWITH",
  EndsWith = "ENDSWITH",
  Matches = "MATCHES",
  LessThan = "LESSTHAN",
  GreaterThan = "GREATERTHAN",
  Before = "BEFORE",
  After = "AFTER",
  BeforeRelative = "BEFORERELATIVE",
  True = "TRUE",
  False = "FALSE",
  Enum = "ENUM",
}

export enum ConditionType {
  And = "and",
  Or = "or",
  Not = "not",
}

export type QueryBuilderValue = {
  query: string
  driver: DeviceType["name"]
}

export type OperatorOption = {
  label: string
  value: OperatorType
  callback(): string
}

export function isOperatorOption(x: unknown): x is OperatorOption {
  return typeof x === "object" && x !== null && typeof (x as OperatorOption).callback === "function"
}
