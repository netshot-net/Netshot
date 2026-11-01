import { createHttpClient, HttpMethod } from "@/api/httpClient"
import type { briefs, components } from "./schema"

/**
 * The client of the REST API: problem details errors (with the field errors), and the dates of the responses as
 * Date objects.
 */
export const client = createHttpClient({
  baseUrl: "/api/v1",
  reviveDates: true,
})

/** The representations of the API, generated from its OpenAPI document (npm run api:types). */
export type Schemas = components["schemas"]

/**
 * The brief view of the representations: what the items of a list read in its default view (or a reference read
 * without expand) have. Ask for view=full (or fields) to get more, and type the items with Schemas then.
 */
export type Brief = briefs

/** A page of a list. */
export type ListResponse<T> = {
  count?: number | null
  offset: number
  limit: number
  next?: string | null
  previous?: string | null
  results: T[]
}

/** The parameters of a list request: filters (field[__lookup]), q, sort, offset/limit and selection. */
export type ListParams = Record<string, string | number | boolean | Array<string | number> | null | undefined>

/** Repeated parameters (status=A&status=B), not the qs default (status[0]=A). */
const QUERY_OPTIONS = { stringifyOpt: { arrayFormat: "repeat" as const } }

/** The media type of a JSON merge patch (PATCH requests). */
export const MERGE_PATCH = "application/merge-patch+json"

/** The media types of the exports of the lists. */
export const EXPORT_TYPES = {
  csv: "text/csv",
  xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
} as const

/**
 * Gets a page of a list.
 *
 * @param path the path of the list (e.g. /devices)
 * @param params the filters, sort, paging and selection; null/undefined values are left out, arrays repeat the parameter
 */
export async function list<T>(path: string, params: ListParams = {}, signal?: AbortSignal) {
  return (await client.get<ListResponse<T>>(path, {
    queryParams: cleanParams(params),
    queryParamsOptions: QUERY_OPTIONS,
    signal,
  })) as ListResponse<T>
}

/**
 * Gets every item of a list in one request (limit=0: no page), for the lists the screens show whole and the choices
 * of the selectors.
 */
export async function listAll<T>(path: string, params: ListParams = {}, signal?: AbortSignal) {
  return (await list<T>(path, { ...params, offset: undefined, limit: 0, count: false }, signal)).results
}

/**
 * Changes some properties of an object (JSON merge patch), if it is still at the version read (ETag).
 */
export async function patch<R, B>(path: string, changes: B, etag?: string | null) {
  const headers: Record<string, string> = { "Content-Type": MERGE_PATCH }
  if (etag) {
    headers["If-Match"] = etag
  }
  return (await client.patch<R, B>(path, changes, { headers })) as R
}

/**
 * Replaces an object, if it is still at the version read (ETag).
 */
export async function replace<R, B>(path: string, body: B, etag?: string | null) {
  return (await client.put<R, B>(path, body, { headers: etag ? { "If-Match": etag } : {} })) as R
}

/**
 * Downloads a list as a file (CSV or Excel), with the filters, sort and selection of the list.
 */
export async function exportList(path: string, format: keyof typeof EXPORT_TYPES, params: ListParams = {}) {
  const response = await client.request(HttpMethod.GET, path, {
    queryParams: cleanParams(params),
    queryParamsOptions: QUERY_OPTIONS,
    headers: { Accept: EXPORT_TYPES[format] },
  })
  const disposition = response.headers.get("Content-Disposition") ?? ""
  const fileName = /filename="([^"]+)"/.exec(disposition)?.[1] ?? `export.${format}`
  return { blob: await response.blob(), fileName }
}

function cleanParams(params: ListParams) {
  const clean: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== "") {
      clean[key] = value
    }
  }
  return clean
}
