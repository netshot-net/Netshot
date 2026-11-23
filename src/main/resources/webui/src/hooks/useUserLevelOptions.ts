import { Level } from "@/types"
import { createOptionHook } from "./createOptionHook"
import { tKey } from "@/i18n/tKey"

export const useUserLevelOptions = createOptionHook([
  {
    label: tKey("user.role.admin"),
    description: tKey("user.role.adminDescription"),
    value: Level.Admin,
  },
  {
    label: tKey("user.role.readWritePlusScripts"),
    description: tKey("user.role.readWritePlusScriptsDescription"),
    value: Level.ExecuteReadWrite,
  },
  {
    label: tKey("user.role.readWrite"),
    description: tKey("user.role.readWriteDescription"),
    value: Level.ReadWrite,
  },
  {
    label: tKey("common.operator"),
    description: tKey("user.role.operatorDescription"),
    value: Level.Operator,
  },
  {
    label: tKey("user.role.readOnly"),
    description: tKey("user.role.readOnlyDescription"),
    value: Level.ReadOnly,
  },
])
