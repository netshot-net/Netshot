import { queryClient } from "@/App"
import { QUERIES } from "@/constants"
import { ListParams } from "@/api/client"
import { Device, DeviceType, Group } from "@/types"
import { create } from "zustand"

export type DeviceSidebarStoreState = {
  query: string
  driver: DeviceType["name"] | null
  total: number
  selected: Device[]
  devices: Device[]
  group: Group | null

  select(devices: Device[]): void
  /** The parameters of the list shown (to export the matching devices). */
  listParams: ListParams | null
  setListParams(params: ListParams | null): void
  selectAll(): Promise<void>
  deselectAll(): void
  isSelected(deviceId: number): boolean
  isSelectedAll(): boolean
  updateQueryAndDriver(query: string, driver: DeviceType["name"] | null): void
  setTotal(total: number): void
  setDevices(devices: Device[]): void
  setGroup(group: Group | null): void
  setQuery(query: string): void
  refresh(): Promise<void>
}

export const useDeviceSidebarStore = create<DeviceSidebarStoreState>((set, get) => ({
  query: "",
  driver: null,
  total: 0,
  selected: [],
  devices: [],
  group: null,
  listParams: null,

  setListParams(listParams: ListParams | null) {
    set({ listParams })
  },

  select(devices: Device[]) {
    set({ selected: devices })
  },

  async selectAll() {
    set({ selected: get().devices })
  },

  deselectAll() {
    set({ selected: [] })
  },

  isSelected(deviceId: number) {
    const selected = get().selected

    return Boolean(selected.find((item) => item.id === deviceId))
  },

  isSelectedAll() {
    const { devices, selected } = get()

    return devices.length > 0 && get().total === selected.length
  },

  updateQueryAndDriver(query: string, driver: DeviceType["name"] | null) {
    set({ query, driver })
  },

  setTotal(total: number) {
    set({ total })
  },

  setDevices(devices: Device[]) {
    set({ devices })
  },

  setGroup(group: Group | null) {
    set({ group })
  },

  setQuery(query: string) {
    set({ query })
  },

  async refresh() {
    await queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_LIST] })
  },
}))
