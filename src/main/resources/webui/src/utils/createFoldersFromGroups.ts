import { Group } from "@/types"

export type Folder = {
  name: string
  // Full path from the root (e.g. "a/b"), unique in the tree unlike the name
  path: string
  children: Array<Folder | Group>
}

/**
 * Check if current object is a group
 */
export function isGroup(x: Folder | Group): x is Group {
  return "type" in x
}

/**
 * Check if current object is a folder
 */
export function isFolder(x: Folder | Group): x is Folder {
  return "children" in x
}

/**
 * Sort folders before groups; folders by name, groups by name then by id.
 */
export function compareFolderOrGroup(a: Folder | Group, b: Folder | Group) {
  const aIsFolder = isFolder(a)
  const bIsFolder = isFolder(b)

  if (aIsFolder !== bIsFolder) {
    return aIsFolder ? -1 : 1
  }

  if (aIsFolder && bIsFolder) {
    return a.name.localeCompare(b.name)
  }

  const group1 = a as Group
  const group2 = b as Group

  return group1.name.localeCompare(group2.name) || group1.id - group2.id
}

/**
 * Split a folder path into its folder names, ignoring the empty ones
 * (leading, trailing or repeated "/", blank names), so "/a//b/" is ["a", "b"].
 */
export function splitFolderPath(folder?: string | null): string[] {
  return (folder ?? "")
    .split("/")
    .map((name) => name.trim())
    .filter((name) => name !== "")
}

export function addFolder(
  paths: string[],
  group: Group,
  children: Folder["children"],
  parentPath = ""
) {
  const name = paths.shift()
  const path = parentPath ? `${parentPath}/${name}` : name
  let folder = children.find((f): f is Folder => isFolder(f) && f.name === name)

  if (!folder) {
    folder = { name, path, children: [] } as Folder
    children.push(folder)
  }

  if (paths.length) {
    addFolder(paths, group, folder.children, path)
  } else {
    folder.children.push(group)
  }

  return children.sort(compareFolderOrGroup)
}

/**
 * Create nested list of folder with groups from netshot backend
 */
export function createFoldersFromGroups(deviceGroups: Group[] = []) {
  return deviceGroups
    .reduce(
      (arr, group) => {
        const paths = splitFolderPath(group.folder)
        if (paths.length === 0) {
          arr.push(group)
        } else {
          addFolder(paths, group, arr)
        }

        return arr
      },
      [] as Array<Folder | Group>
    )
    .sort(compareFolderOrGroup)
}

interface FindResult {
  node: Folder | Group
  path: Folder[]
}

export function findNodeWithPath(
  tree: Array<Folder | Group>,
  id: number,
  path: Folder[] = []
): FindResult | null {
  for (const node of tree) {
    if (isGroup(node) && node.id === id) {
      return { node, path }
    }

    if (isFolder(node)) {
      const result = findNodeWithPath(node.children, id, [...path, node])
      if (result) return result
    }
  }

  return null
}
