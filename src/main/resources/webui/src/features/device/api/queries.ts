import api from "@/api"
import { Brief, list, listAll, ListParams, ListResponse, Schemas } from "@/api/client"
import { NetshotError } from "@/api/httpClient"
import { QUERIES } from "@/constants"
import { Config, Device, DeviceFamily, DeviceTag, DeviceType } from "@/types"
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

/** The global credential sets (to pick one for an access of a device). */
export function useCredentialSets() {
  return useQuery({
    queryKey: [QUERIES.CREDENTIAL_SET_LIST, "device-accesses"],
    queryFn: async () => listAll<Brief["CredentialSet"]>("/credential-sets", { sort: "name" }),
  })
}

/** The Vault instances (to read the secrets of an access from). */
export function useVaultInstanceChoices() {
  return useQuery({
    queryKey: ["vault-instance-choices"],
    queryFn: async () => listAll<Schemas["VaultInstance"]>("/vault-instances", { fields: "name", sort: "name" }),
  })
}

export function useDeviceConfigs(deviceId: number | undefined) {
  return useQuery({
    queryKey: [QUERIES.DEVICE_CONFIGS, deviceId],
    queryFn: async () => listAll<Schemas["Config"]>(`/devices/${deviceId}/configs`, { view: "full" }),
    select: (data) => data as Config[],
    enabled: !!deviceId,
  })
}

/** The parameters of the list of devices of the sidebar. */
export function deviceListParams(groupId: number, query = ""): ListParams {
  return {
    sort: "name",
    group: groupId || undefined,
    query: query || undefined,
  }
}

/** The cache key of the list of devices of the sidebar. */
export function deviceListKey(params: ListParams) {
  return [QUERIES.DEVICE_LIST, params]
}

/**
 * The devices of the sidebar list: all the devices, or those of a group, or those matching a search query
 * (Netshot query language), loaded whole (the list is virtualized). With a query, also gives the query as the server
 * applies it (canonical form), or the error of an invalid query.
 */
export function useDevices(groupId: number, query = "", enabled = true) {
  const params = deviceListParams(groupId, query)
  const result = useQuery<ListResponse<Device>, NetshotError>({
    queryKey: deviceListKey(params),
    queryFn: async ({ signal }) => list<Device>("/devices", { ...params, offset: undefined, limit: 0 }, signal),
    enabled,
  })
  return {
    rows: result.data?.results ?? [],
    appliedQuery: result.data?.query ?? undefined,
    error: result.error,
    isPending: result.isPending,
  }
}

export function useDeviceFamilies() {
  return useQuery({
    queryKey: [QUERIES.DEVICE_FAMILY_LIST],
    queryFn: async () => (await api.device.getAllFamilies()) ?? [],
    select: useCallback(
      (data: DeviceFamily[]) =>
        Array.from(new Set(data.map((f) => f.family).filter((f): f is string => Boolean(f)))).sort(),
      []
    ),
  })
}

export function useDevicePartNumbers() {
  return useQuery({
    queryKey: [QUERIES.DEVICE_PART_NUMBER_LIST],
    queryFn: async () => (await api.device.getAllPartNumbers()) ?? [],
    select: useCallback(
      (data: Schemas["PartNumber"][]) =>
        Array.from(new Set(data.map((p) => p.partNumber).filter((p): p is string => Boolean(p)))).sort(),
      []
    ),
  })
}

export function useDeviceSoftwareVersions() {
  return useQuery({
    queryKey: [QUERIES.DEVICE_SOFTWARE_VERSION_LIST],
    queryFn: async () => (await api.device.getAllSoftwareVersions()) ?? [],
    select: useCallback(
      (data: Schemas["SoftwareVersion"][]) =>
        Array.from(new Set(data.map((v) => v.version).filter((v): v is string => Boolean(v)))).sort(),
      []
    ),
  })
}

export function useDeviceTags() {
  return useQuery({
    queryKey: [QUERIES.DEVICE_TAG_LIST],
    queryFn: async () => (await api.device.getAllTags()) ?? [],
    select: useCallback(
      (data: DeviceTag[]) => data.map((tag) => tag.name).filter((n): n is string => Boolean(n)),
      []
    ),
  })
}
