import api from "@/api"

export function fetchDomains() {
  return api.admin.fetchAllDomains()
}

export function fetchVaultInstances() {
  return api.admin.fetchAllVaultInstances()
}
