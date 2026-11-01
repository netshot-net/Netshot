import { Brief, client, list, listAll, ListParams, replace, Schemas } from "./client"

export type ApiToken = Schemas["ApiToken"]
export type ApiTokenCreated = Schemas["ApiTokenCreated"]
export type ApiTokenRequest = Schemas["ApiTokenRequest"]
export type ClusterMember = Schemas["ClusterMember"]
export type CredentialSet = Schemas["CredentialSet"]
export type CredentialSetRequest = Schemas["CredentialSetRequest"]
export type DeviceType = Schemas["DeviceType"]
export type Domain = Schemas["Domain"]
export type User = Schemas["User"]
export type UserRequest = Schemas["UserRequest"]
export type VaultConnectionTest = Schemas["VaultConnectionTest"]
export type VaultInstance = Schemas["VaultInstance"]
export type VaultInstanceRequest = Schemas["VaultInstanceRequest"]

// The generated types of the hooks are not usable as they are (the hook type is polymorphic): the web hook, the
// only kind of hook for now, is described here.
export type HookTrigger = Schemas["HookTrigger"]

export type Hook = {
  id?: number
  url?: string
  display?: string
  type: "WEB"
  name: string
  enabled: boolean
  triggers: HookTrigger[]
  action: "POST_JSON" | "POST_XML" | "POST_YAML"
  targetUrl: string
  httpsCaTrustMode: "TRUST_ANY" | "SYSTEM_TRUSTSTORE" | "CUSTOM_CA"
  httpsCustomCaCertificate?: string | null
}

export type HookRequest = Omit<Hook, "id" | "url" | "display">

/** The ETag (If-Match) of a versioned object. */
export function etagOf(object: { version?: number } | null | undefined) {
  return object?.version === undefined || object.version === null ? null : `"${object.version}"`
}

function ifMatch(version?: number | null) {
  return version === undefined || version === null ? undefined : { "If-Match": `"${version}"` }
}

async function getAllApiTokens(params: ListParams = {}) {
  return list<Brief["ApiToken"]>("/api-tokens", params)
}

async function createApiToken(payload: ApiTokenRequest) {
  return (await client.post<ApiTokenCreated, ApiTokenRequest>("/api-tokens", payload)) as ApiTokenCreated
}

async function removeApiToken(id: number) {
  return client.delete(`/api-tokens/${id}`)
}

async function getAllCredentialSets(params: ListParams = {}) {
  return list<Brief["CredentialSet"]>("/credential-sets", params)
}

/** All the global credential sets (the choices of the devices' accesses). */
async function fetchAllCredentialSets() {
  return listAll<CredentialSet>("/credential-sets", { sort: "name", view: "full" })
}

async function createCredentialSet(payload: CredentialSetRequest) {
  return (await client.post<CredentialSet, CredentialSetRequest>("/credential-sets", payload)) as CredentialSet
}

async function updateCredentialSet(id: number, payload: CredentialSetRequest, version?: number | null) {
  return replace<CredentialSet, CredentialSetRequest>(`/credential-sets/${id}`, payload, etagOf({ version: version ?? undefined }))
}

async function removeCredentialSet(id: number, version?: number | null) {
  return client.delete(`/credential-sets/${id}`, { headers: ifMatch(version) })
}

async function getAllDomains(params: ListParams = {}) {
  return list<Brief["Domain"]>("/domains", params)
}

/** All the domains (the choices of the domain selectors). */
async function fetchAllDomains() {
  return listAll<Brief["Domain"]>("/domains", { sort: "name" })
}

async function createDomain(payload: Partial<Domain>) {
  return (await client.post<Domain, Partial<Domain>>("/domains", payload)) as Domain
}

async function updateDomain(id: number, payload: Partial<Domain>, version?: number | null) {
  return replace<Domain, Partial<Domain>>(`/domains/${id}`, payload, etagOf({ version: version ?? undefined }))
}

async function removeDomain(id: number, version?: number | null) {
  return client.delete(`/domains/${id}`, { headers: ifMatch(version) })
}

async function getAllHooks(params: ListParams = {}) {
  return list<Brief["WebHook"]>("/hooks", params)
}

async function createHook(payload: HookRequest) {
  return (await client.post<Hook, HookRequest>("/hooks", payload)) as Hook
}

async function updateHook(id: number, payload: HookRequest) {
  return replace<Hook, HookRequest>(`/hooks/${id}`, payload)
}

async function removeHook(id: number) {
  return client.delete(`/hooks/${id}`)
}

async function getAllUsers(params: ListParams = {}) {
  return list<Brief["User"]>("/users", params)
}

async function createUser(payload: UserRequest) {
  return (await client.post<User, UserRequest>("/users", payload)) as User
}

async function updateUser(id: number, payload: UserRequest) {
  return replace<User, UserRequest>(`/users/${id}`, payload)
}

async function removeUser(id: number) {
  await client.delete(`/users/${id}`)
}

async function getAllDrivers(params: ListParams = {}) {
  return list<Brief["DeviceType"]>("/device-types", params)
}

/**
 * Reloads the device drivers from their sources (on every cluster member).
 */
async function reloadDrivers() {
  await client.post<null, null>("/device-types/reload", null)
}

async function getAllVaultInstances(params: ListParams = {}) {
  return list<Brief["VaultInstance"]>("/vault-instances", params)
}

/** All the Vault instances (the choices of the Vault-backed fields). */
async function fetchAllVaultInstances() {
  return listAll<Brief["VaultInstance"]>("/vault-instances", { sort: "name" })
}

async function createVaultInstance(payload: VaultInstanceRequest) {
  return (await client.post<VaultInstance, VaultInstanceRequest>("/vault-instances", payload)) as VaultInstance
}

async function updateVaultInstance(id: number, payload: VaultInstanceRequest, version?: number | null) {
  return replace<VaultInstance, VaultInstanceRequest>(`/vault-instances/${id}`, payload, etagOf({ version: version ?? undefined }))
}

async function removeVaultInstance(id: number, version?: number | null) {
  return client.delete(`/vault-instances/${id}`, { headers: ifMatch(version) })
}

/** Tries to log in to a Vault instance definition which isn't saved (secrets included). */
async function testVaultInstance(payload: VaultInstanceRequest) {
  return (await client.post<VaultConnectionTest, VaultInstanceRequest>("/vault-instances/test", payload)) as VaultConnectionTest
}

/** Tries to log in to a saved Vault instance. */
async function testSavedVaultInstance(id: number) {
  return (await client.post<VaultConnectionTest, null>(`/vault-instances/${id}/test`, null)) as VaultConnectionTest
}

async function getAllClusterMembers() {
  return list<ClusterMember>("/system/cluster/members", { view: "full" })
}

export default {
  getAllApiTokens,
  createApiToken,
  removeApiToken,
  getAllCredentialSets,
  fetchAllCredentialSets,
  createCredentialSet,
  updateCredentialSet,
  removeCredentialSet,
  getAllDomains,
  fetchAllDomains,
  createDomain,
  updateDomain,
  removeDomain,
  getAllHooks,
  createHook,
  updateHook,
  removeHook,
  getAllUsers,
  createUser,
  updateUser,
  removeUser,
  getAllDrivers,
  reloadDrivers,
  getAllVaultInstances,
  fetchAllVaultInstances,
  createVaultInstance,
  updateVaultInstance,
  removeVaultInstance,
  testVaultInstance,
  testSavedVaultInstance,
  getAllClusterMembers,
}
