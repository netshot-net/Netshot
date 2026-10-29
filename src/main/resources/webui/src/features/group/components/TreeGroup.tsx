import { Group } from "@/types"
import { findNodeWithPath, Folder, isGroup } from "@/utils"
import { JSX } from "react"
import GroupOrFolderItem from "./GroupOrFolderItem"
import { TreeGroupProvider } from "./TreeGroupProvider"

type FolderOrGroup = Folder | Group

export type TreeGroupProps = {
  items: FolderOrGroup[]
  onGroupSelect?(group: Group): void
  isSelected?(group: Group): boolean
  renderGroupChildren?(group: Group): JSX.Element
  isFolderOpen(key: string): boolean
  toggleFolderOpen(key: string): void
  showMenu?: boolean
}

export function TreeGroup({
  items,
  onGroupSelect,
  isSelected,
  renderGroupChildren,
  isFolderOpen,
  toggleFolderOpen,
  showMenu = false,
}: TreeGroupProps) {
  return (
    <TreeGroupProvider
      isFolderOpen={isFolderOpen}
      toggleFolderOpen={toggleFolderOpen}
      onGroupSelect={onGroupSelect}
      isSelected={isSelected}
      renderGroupChildren={renderGroupChildren}
      showMenu={showMenu}
    >
      {items?.map((item) => (
        <GroupOrFolderItem item={item} key={isGroup(item) ? item?.id : item?.name} />
      ))}
    </TreeGroupProvider>
  )
}

export function getExpandedKeys(items: FolderOrGroup[], groupId: number) {
  const result = findNodeWithPath(items, groupId)
  return result ? result.path.map((p) => p.path) : []
}

/**
 * Flattens a folder/group tree into the groups currently visible (i.e. not
 * hidden inside a collapsed folder), in the same top-to-bottom order they render.
 */
export function getVisibleGroups(
  items: FolderOrGroup[],
  isFolderOpen: (key: string) => boolean
): Group[] {
  const result: Group[] = []

  for (const item of items ?? []) {
    if (isGroup(item)) {
      result.push(item)
    } else if (isFolderOpen(item.path)) {
      result.push(...getVisibleGroups(item.children, isFolderOpen))
    }
  }

  return result
}
