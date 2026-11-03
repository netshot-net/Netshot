import { list, ListParams, ListResponse } from "@/api/client"
import { QueryKey, useInfiniteQuery } from "@tanstack/react-query"
import { useMemo } from "react"

export type UseListConfig = {
  /** The number of items of a page (default 50). */
  limit?: number
  /** False to suspend the query. */
  enabled?: boolean
}

/**
 * Loads a list page by page, for an infinite scroll: the next page is requested by onBottomReached, as long as
 * the server gives a next page. The items aren't counted (the count of a large list costs more than its pages).
 *
 * @example
 * const { rows, isPending, onBottomReached } = useList<Schemas["Domain"]>(
 *   [QUERIES.DOMAIN_LIST, query], "/domains", { q: query, sort: "name" })
 */
export function useList<T>(queryKey: QueryKey, path: string, params: ListParams = {}, config: UseListConfig = {}) {
  const limit = config.limit ?? 50

  const query = useInfiniteQuery({
    queryKey: [...queryKey, params, limit],
    queryFn: async ({ pageParam, signal }) => list<T>(path, { ...params, offset: pageParam, limit }, signal),
    initialPageParam: 0,
    getNextPageParam(lastPage: ListResponse<T>) {
      return lastPage?.next ? lastPage.offset + lastPage.limit : undefined
    },
    enabled: config.enabled,
  })

  const rows = useMemo(() => query.data?.pages?.flatMap((page) => page.results) ?? [], [query.data])

  function onBottomReached() {
    if (query.isFetchingNextPage || !query.hasNextPage) {
      return
    }
    query.fetchNextPage()
  }

  return {
    rows,
    isPending: query.isPending,
    isFetching: query.isFetching,
    hasNextPage: query.hasNextPage,
    onBottomReached,
    refetch: query.refetch,
  }
}
