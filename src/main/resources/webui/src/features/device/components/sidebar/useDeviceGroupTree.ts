import api from "@/api"
import { getExpandedKeys, getVisibleGroups } from "@/features/group"
import { useTreeOpenKeys } from "@/hooks"
import { QUERIES } from "@/constants"
import { useArrowKeyNavigation } from "@/hooks"
import { GroupBrief } from "@/types"
import { createFoldersFromGroups, Folder, isGroup } from "@/utils"
import { useQuery } from "@tanstack/react-query"
import { useCallback, useEffect, useMemo } from "react"
import { useLocation, useNavigate, useSearchParams } from "react-router"
import { useDeviceSidebarStore } from "../../stores"

function findGroupInTree(items: Array<Folder | GroupBrief>, id: number): GroupBrief | null {
  for (const item of items) {
    if (isGroup(item)) {
      if (item.id === id) return item
    } else {
      const found = findGroupInTree(item.children, id)
      if (found) return found
    }
  }
  return null
}

export type UseDeviceGroupTreeOptions = {
  /** Whether the popover displaying the tree is currently open; scopes arrow-key nav. */
  enabled: boolean
  onAfterSelect?(): void
}

/**
 * Fetches the group list and keeps the sidebar store's selected group in sync with
 * the "?group=" URL param. Runs regardless of whether the tree UI is actually
 * shown, so a deep link (e.g. "?group=5") resolves to a group name for the
 * search box badge even before the popover is ever opened.
 */
export function useDeviceGroupTree(options: UseDeviceGroupTreeOptions) {
  const { enabled, onAfterSelect } = options
  const location = useLocation()
  const navigate = useNavigate()
  const setGroup = useDeviceSidebarStore((state) => state.setGroup)
  const updateQueryAndDriver = useDeviceSidebarStore((state) => state.updateQueryAndDriver)
  const [searchParams] = useSearchParams()
  const selectedGroupId = +(searchParams.get("group") ?? 0)

  const { data: items, isPending } = useQuery({
    queryKey: [QUERIES.DEVICE_GROUPS],
    queryFn: async () => api.group.getAll(),
    select: createFoldersFromGroups,
  })

  useEffect(() => {
    if (isPending) return
    if (!selectedGroupId) {
      setGroup(null)
      return
    }
    setGroup(findGroupInTree(items ?? [], selectedGroupId))
  }, [selectedGroupId, items, isPending, setGroup])

  const onGroupSelect = useCallback(
    (group: GroupBrief) => {
      if (group?.id === selectedGroupId) {
        onAfterSelect?.()
        return
      }

      updateQueryAndDriver("", null)

      navigate(
        {
          pathname: location.pathname,
          search: `?group=${group.id}`,
        },
        { replace: true }
      )

      setGroup(group)
      onAfterSelect?.()
    },
    [selectedGroupId, navigate, location.pathname, setGroup, updateQueryAndDriver, onAfterSelect]
  )

  const expandedKeys = useMemo(() => {
    if ((items?.length ?? 0) > 0 && selectedGroupId) {
      return getExpandedKeys(items ?? [], selectedGroupId)
    }
    return []
  }, [items, selectedGroupId])

  const { isOpen, toggle } = useTreeOpenKeys(expandedKeys)

  const visibleGroups = useMemo(() => getVisibleGroups(items ?? [], isOpen), [items, isOpen])
  const activeIndex = visibleGroups.findIndex((group) => group.id === selectedGroupId)

  useArrowKeyNavigation({
    items: visibleGroups,
    activeIndex,
    onNavigate: onGroupSelect,
    enabled,
  })

  return {
    items: items ?? [],
    isPending,
    selectedGroupId,
    onGroupSelect,
    isFolderOpen: isOpen,
    toggleFolderOpen: toggle,
  }
}
