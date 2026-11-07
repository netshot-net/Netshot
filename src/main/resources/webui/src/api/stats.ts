import { client, ListParams } from "./client"

/** One row of a statistics answer: the counted values (references, strings, dates...), the time bucket start, the count. */
export type StatsRow = { count: number; start?: Date } & Record<string, unknown>

/** The answer of a statistics request (/devices/stats, /tasks/stats, /configs/stats...). */
export type StatsResponse<R extends StatsRow = StatsRow> = {
  count: number | null
  groupBy: string[] | null
  interval: string | null
  on: string | null
  from: Date | null
  to: Date | null
  timeZone: string | null
  results: R[]
}

/** The reference of an object in a statistics row. */
export type StatsRef = { id: number; display?: string | null }

/**
 * Counts the objects of a list (statistics): by the values of up to two fields (groupBy) and/or by time bucket (interval),
 * with the filters of the list.
 *
 * @param path the path of the statistics (e.g. /devices/stats)
 * @param params groupBy, interval, from/to, timeZone and the filters of the list; arrays repeat the parameter
 */
export async function getStats<R extends StatsRow = StatsRow>(path: string, params: ListParams = {}) {
  const queryParams: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== "") {
      queryParams[key] = value
    }
  }
  const stats = (await client.get<StatsResponse<R>>(path, {
    queryParams,
    queryParamsOptions: { stringifyOpt: { arrayFormat: "repeat" } },
  })) as StatsResponse<R>
  return { ...stats, results: stats.results ?? [] }
}
