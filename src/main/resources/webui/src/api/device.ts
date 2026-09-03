import {
  Config,
  Device,
  DeviceComplianceResult,
  DeviceDiagnosticResult,
  DeviceFamily,
  DeviceInterface,
  DeviceModule,
  DeviceType,
  LightDevice,
} from "@/types"
import { LightTask, Task } from "@/types/task"
import withQuery from "with-query"
import httpClient from "./httpClient"
import {
  CreateDevicePayload,
  DeviceModuleQueryParams,
  DeviceQueryParams,
  DeviceSearchPayload,
  DeviceSearchResult,
  PaginationQueryParams,
  UpdateDevicePayload,
} from "./types"

async function search(payload: DeviceSearchPayload) {
  return httpClient.post<DeviceSearchResult, DeviceSearchPayload>(
    withQuery("/devices/search", {
      offset: payload.offset,
      limit: payload.limit,
    }),
    {
      query: payload.query,
    }
  )
}

async function getAll(queryParams: DeviceQueryParams = {}) {
  return httpClient.get<LightDevice[]>("/devices", {
    queryParams,
  })
}

async function getById(id: number) {
  return httpClient.get<Device>(`/devices/${id}`)
}

async function create(payload: CreateDevicePayload) {
  return httpClient.post<Task, CreateDevicePayload>("/devices", payload)
}

async function update(id: number, payload: Partial<UpdateDevicePayload>) {
  return httpClient.put<Device, Partial<UpdateDevicePayload>>(`/devices/${id}`, payload)
}

async function remove(id: number) {
  return httpClient.delete(`/devices/${id}`)
}

async function getComplianceResultById(id: number, queryParams: PaginationQueryParams = {}) {
  return httpClient.get<DeviceComplianceResult[]>(`/devices/${id}/complianceresults`, {
    queryParams,
  })
}

async function getDiagnosticResultById(id: number, queryParams: PaginationQueryParams = {}) {
  return httpClient.get<DeviceDiagnosticResult[]>(`/devices/${id}/diagnosticresults`, {
    queryParams,
  })
}

async function getAllDeviceConfigsById(id: number, queryParams: PaginationQueryParams = {}) {
  return httpClient.get<Config[]>(`/devices/${id}/configs`, {
    queryParams,
  })
}

/**
 * @todo: Add endpoint to get current config from device
 */
async function getCurrentConfig(deviceId: number) {
  const configs = await getAllDeviceConfigsById(deviceId, {
    limit: 1,
  })

  return configs?.[0]
}

async function getAllInterfacesById(id: number, queryParams: PaginationQueryParams = {}) {
  return httpClient.get<DeviceInterface[]>(`/devices/${id}/interfaces`, {
    queryParams,
  })
}

async function getAllModulesById(id: number, queryParams: DeviceModuleQueryParams = {}) {
  return httpClient.get<DeviceModule[]>(`/devices/${id}/modules`, {
    queryParams,
  })
}

async function getAllTasksById(id: number, queryParams: PaginationQueryParams = {}) {
  return httpClient.get<LightTask[]>(`/devices/${id}/tasks`, {
    queryParams,
  })
}

async function getAllFamilies(queryParams: PaginationQueryParams = {}) {
  return httpClient.get<DeviceFamily[]>("/devicefamilies", {
    queryParams,
  })
}

async function getAllPartNumbers(queryParams: PaginationQueryParams = {}) {
  return httpClient.get<
    Array<{
      partNumber: string
    }>
  >(`/partnumbers`, {
    queryParams,
  })
}

async function getAllSoftwareVersions(queryParams: PaginationQueryParams = {}) {
  return httpClient.get<Array<{ version: string }>>("/softwareversions", {
    queryParams,
  })
}

async function getAllTypes() {
  return httpClient.get<DeviceType[]>(`/devicetypes`)
}

export default {
  search,
  getAll,
  getById,
  create,
  update,
  remove,
  getComplianceResultById,
  getDiagnosticResultById,
  getAllConfigsById: getAllDeviceConfigsById,
  getCurrentConfig,
  getAllInterfacesById,
  getAllModulesById,
  getAllTasksById,
  getAllFamilies,
  getAllPartNumbers,
  getAllSoftwareVersions,
  getAllTypes,
}
