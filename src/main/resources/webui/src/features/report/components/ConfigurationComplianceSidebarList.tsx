import { getExpandedKeys, getVisibleGroups, TreeGroup } from "@/features/group"
import { useTreeOpenKeys } from "@/hooks"
import { useArrowKeyNavigation } from "@/hooks"
import { Group } from "@/types"
import { createFoldersFromGroups, Folder, search } from "@/utils"
import { Skeleton, Stack } from "@chakra-ui/react"
import { useCallback, useMemo } from "react"
import { useNavigate, useParams } from "react-router"
import { useGroupConfigComplianceStats } from "../hooks"
import { useConfigurationComplianceSidebarStore } from "../stores/useConfigurationComplianceSidebarStore"

export default function ConfigurationCompliantSidebarList() {
  const navigate = useNavigate()
  const query = useConfigurationComplianceSidebarStore((state) => state.query)
  const params = useParams<{ id: string }>()

  const { groups, stats, isPending } = useGroupConfigComplianceStats()

  const items = useMemo((): (Group | Folder)[] => {
    if (!groups) return []

    const nonEmptyGroupIds = new Set(stats.map((stat) => stat.groupId))
    const nonEmptyGroups = groups.filter((group) => nonEmptyGroupIds.has(group.id))

    return createFoldersFromGroups(search(nonEmptyGroups, "name").with(query))
  }, [groups, stats, query])

  const onGroupSelect = useCallback(
    (group: Group) => {
      navigate(`./${group.id}`, {
        replace: true,
      })
    },
    [navigate]
  )

  const expandedKeys = useMemo(() => {
    if (items?.length > 0 && params.id) {
      return getExpandedKeys(items, +(params.id ?? 0))
    }
    return []
  }, [items, params.id])

  const { isOpen, toggle } = useTreeOpenKeys(expandedKeys)

  const visibleGroups = useMemo(() => getVisibleGroups(items ?? [], isOpen), [items, isOpen])
  const activeIndex = visibleGroups.findIndex((group) => group.id === +(params.id ?? 0))

  useArrowKeyNavigation({
    items: visibleGroups,
    activeIndex,
    onNavigate: onGroupSelect,
  })

  return (
    <Stack px="6" pt="3" flex="1" overflow="auto" gap="0">
      {isPending ? (
        <Stack gap="3" pb="6">
          <Skeleton height="36px" />
          <Skeleton height="36px" />
          <Skeleton height="36px" />
          <Skeleton height="36px" />
        </Stack>
      ) : (
        <TreeGroup
          items={items}
          isFolderOpen={isOpen}
          toggleFolderOpen={toggle}
          onGroupSelect={onGroupSelect}
          isSelected={(group) => group.id === +(params.id ?? 0)}
        />
      )}
    </Stack>
  )
}
