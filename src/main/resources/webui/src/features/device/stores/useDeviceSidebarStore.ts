import { queryClient } from "@/App"
import { QUERIES } from "@/constants"
import { DeviceType, Group, LightDevice } from "@/types"
import { create } from "zustand"
import { QUERIES as DEVICE_QUERIES } from "../constants"

export type DeviceSidebarStoreState = {
  query: string
  driver: DeviceType["name"] | null
  total: number
  selected: LightDevice[]
  devices: LightDevice[]
  group: Group | null

  select(devices: LightDevice[]): void
  selectAll(): void
  deselectAll(): void
  isSelected(deviceId: number): boolean
  isSelectedAll(): boolean
  updateQueryAndDriver(query: string, driver: DeviceType["name"] | null): void
  setTotal(total: number): void
  setDevices(devices: LightDevice[]): void
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

  select(devices: LightDevice[]) {
    set({ selected: devices })
  },

  selectAll() {
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

    return devices.length > 0 && devices.length === selected.length
  },

  updateQueryAndDriver(query: string, driver: DeviceType["name"] | null) {
    set({ query, driver })
  },

  setTotal(total: number) {
    set({ total })
  },

  setDevices(devices: LightDevice[]) {
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
    await queryClient.invalidateQueries({
      queryKey: [DEVICE_QUERIES.DEVICE_SEARCH_LIST],
    })
  },
}))
