import type { Schemas } from "@/api/client"

export enum GroupType {
  Static = "STATIC",
  Dynamic = "DYNAMIC",
}

/**
 * A device group as the API gives it: the brief view of a list gives its name, folder and type, the full view
 * the other properties (version, change date, hiddenFromReports and query).
 */
export type Group = Omit<Schemas["Group"], "name" | "folder" | "type"> & {
  name: string
  folder: string
  type: GroupType
}
