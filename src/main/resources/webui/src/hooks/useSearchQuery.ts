import { useCallback, useState } from "react"
import { useThrottle } from "./useThrottle"

/**
 * The text of the search box of a list screen: query is the throttled text, to put in the request (q) or to filter
 * the loaded rows with.
 *
 * @example
 * const searchBox = useSearchQuery()
 * const { rows } = useListAll<Brief["Domain"]>([QUERIES.DOMAIN_LIST, searchBox.query], "/domains", { q: searchBox.query })
 * <Search onQuery={searchBox.onQuery} onClear={searchBox.onQueryClear} />
 */
export function useSearchQuery(initialQuery?: string) {
  const [innerQuery, setInnerQuery] = useState<string | undefined>(initialQuery)
  const query = useThrottle(innerQuery)

  const onQuery = useCallback((value: string) => {
    setInnerQuery(value)
  }, [])

  const onQueryClear = useCallback(() => {
    setInnerQuery("")
  }, [])

  return { query, onQuery, onQueryClear }
}
