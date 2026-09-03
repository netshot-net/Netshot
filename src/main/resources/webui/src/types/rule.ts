import { DeviceComplianceResultType } from "./device";

export type Rule = {
  anyBlock: boolean;
  context: string;
  deviceDriver: string;
  deviceDriverDescription: string;
  enabled: boolean;
  field: string;
  id: number;
  invert: boolean;
  matchAll: boolean;
  name: string;
  normalize: boolean;
  regExp: boolean;
  text: string;
  type: RuleType;
  script: string;
};

export enum RuleType {
  Text = "TextRule",
  Javascript = "JavaScriptRule",
  Python = "PythonRule",
}

export type LightRule = {
  id: number;
  name: string;
  type: RuleType;
  enabled: boolean;
  policyId: number;
};

export type TestRuleResult = {
  result: DeviceComplianceResultType;
  scriptError: string;
  comment: string;
};
