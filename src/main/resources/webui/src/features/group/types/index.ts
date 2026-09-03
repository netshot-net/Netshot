import { LightDevice } from "@/types"

export enum FormStep {
  Type,
  Details,
}

export type GroupForm = {
  name: string
  folder: string
  visibleInReports: boolean
  staticDevices: LightDevice[]
  query: string
}
