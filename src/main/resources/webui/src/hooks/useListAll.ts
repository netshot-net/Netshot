import { listAll, ListParams } from "@/api/client"
import { QueryKey, useQuery } from "@tanstack/react-query"

/**
 * Loads a whole list (every page), for the screens which show all the items at once (the device list, the
 * administration lists, the rules...); the task lists, which can be long, use useList (infinite scroll) instead.
 *
 * @example
 * const { rows, isPending } = useListAll<Schemas["Domain"]>([QUERIES.DOMAIN_LIST], "/domains", { q: query, sort: "name" })
 */
export function useListAll<T>(queryKey: QueryKey, path: string, params: ListParams = {}, config: { enabled?: boolean } = {}) {
  const query = useQuery({
    queryKey: [...queryKey, params],
    queryFn: async ({ signal }) => listAll<T>(path, params, signal),
    enabled: config.enabled,
  })

  return {
    rows: query.data ?? [],
    isPending: query.isPending,
    isFetching: query.isFetching,
    refetch: query.refetch,
  }
}
