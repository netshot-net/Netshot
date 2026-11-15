import { Device } from "@/types"

export enum FormStep {
  Type,
  Details,
}

// The query a new dynamic group starts with (the API requires one: an empty query selects all the devices)
export const DEFAULT_DYNAMIC_GROUP_QUERY = '[Name] is "example"'

export type GroupForm = {
  name: string
  folder: string
  visibleInReports: boolean
  staticDevices: Device[]
  query: string
}
