import api, { DeviceQueryParams } from "@/api"
import { DeviceSearchResult } from "@/api/types"
import { QUERIES } from "@/constants"
import { DeviceFamily, DeviceType, LightDevice } from "@/types"
import { sortAlphabetical } from "@/utils"
import { useQuery } from "@tanstack/react-query"
import { useCallback } from "react"

export function useDevice(id: number) {
  return useQuery({
    queryKey: [QUERIES.DEVICE_DETAIL, id],
    queryFn: async () => api.device.getById(id),
  })
}

export function useDeviceTypes() {
  return useQuery({
    queryKey: [QUERIES.DEVICE_TYPE_LIST],
    queryFn: async () => (await api.device.getAllTypes()) ?? [],
    initialData: [],
  })
}

export function useDeviceTypesWithOptions() {
  return useQuery({
    queryKey: [QUERIES.DEVICE_TYPE_LIST],
    queryFn: async () => (await api.device.getAllTypes()) ?? [],
    select: useCallback((types: DeviceType[]) => {
      return sortAlphabetical(
        types.map((type) => ({
          label: type?.description,
          value: type,
        })),
        "label"
      )
    }, []),
    initialData: [],
  })
}

export function useCredentialSets() {
  return useQuery({
    queryKey: [QUERIES.CREDENTIAL_SET_LIST],
    queryFn: async () =>
      api.admin.getAllCredentialSets({}),
  })
}

export function useDeviceConfigs(deviceId: number | undefined) {
  return useQuery({
    queryKey: [QUERIES.DEVICE_CONFIGS, deviceId],
    queryFn: async () => {
      return (await api.device.getAllConfigsById(deviceId!)) ?? []
    },
    enabled: !!deviceId,
  })
}

export function useDevices(groupId: number) {
  return useQuery({
    queryKey: [QUERIES.DEVICE_LIST, groupId],
    queryFn: async () => {
      const params = {} as DeviceQueryParams

      if (groupId) {
        params.group = groupId
      }

      return (await api.device.getAll(params)) ?? []
    },
    select: useCallback(
      (data: LightDevice[]) => (groupId ? data : sortAlphabetical(data, "name")),
      [groupId]
    ),
  })
}

export function useDeviceFamilies() {
  return useQuery({
    queryKey: [QUERIES.DEVICE_FAMILY_LIST],
    queryFn: async () => (await api.device.getAllFamilies({ limit: 99999 })) ?? [],
    select: useCallback(
      (data: DeviceFamily[]) =>
        Array.from(new Set(data.map((f) => f.deviceFamily).filter(Boolean))).sort(),
      []
    ),
  })
}

export function useDevicePartNumbers() {
  return useQuery({
    queryKey: [QUERIES.DEVICE_PART_NUMBER_LIST],
    queryFn: async () => (await api.device.getAllPartNumbers({ limit: 99999 })) ?? [],
    select: useCallback(
      (data: Array<{ partNumber: string }>) =>
        Array.from(new Set(data.map((p) => p.partNumber).filter(Boolean))).sort(),
      []
    ),
  })
}

export function useDeviceSoftwareVersions() {
  return useQuery({
    queryKey: [QUERIES.DEVICE_SOFTWARE_VERSION_LIST],
    queryFn: async () => (await api.device.getAllSoftwareVersions({ limit: 99999 })) ?? [],
    select: useCallback(
      (data: Array<{ version: string }>) =>
        Array.from(new Set(data.map((v) => v.version).filter(Boolean))).sort(),
      []
    ),
  })
}

export function useSearchDevices(query: string) {
  return useQuery({
    queryKey: [QUERIES.DEVICE_SEARCH_LIST, query],
    queryFn: async () => (await api.device.search({ query })) ?? { query, devices: [] },
    select: useCallback(
      (data: DeviceSearchResult) => ({ ...data, devices: sortAlphabetical(data.devices, "name") }),
      []
    ),
  })
}
