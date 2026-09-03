import { Group } from "./group";
import { LightRule } from "./rule";

export type Policy = {
  id: number;
  name: string;
  targetGroups: Group[];
  rules?: LightRule[];
};
