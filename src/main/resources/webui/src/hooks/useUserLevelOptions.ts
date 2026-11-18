import { Level } from "@/types"
import { createOptionHook } from "./createOptionHook"

export const useUserLevelOptions = createOptionHook([
  {
    label: "user.role.admin",
    description: "user.role.adminDescription",
    value: Level.Admin,
  },
  {
    label: "user.role.readWritePlusScripts",
    description: "user.role.readWritePlusScriptsDescription",
    value: Level.ExecuteReadWrite,
  },
  {
    label: "user.role.readWrite",
    description: "user.role.readWriteDescription",
    value: Level.ReadWrite,
  },
  {
    label: "common.operator",
    description: "user.role.operatorDescription",
    value: Level.Operator,
  },
  {
    label: "user.role.visitor",
    description: "user.role.visitorDescription",
    value: Level.ReadOnly,
  },
])
