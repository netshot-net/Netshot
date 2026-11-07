import { ScriptBrief } from "@/types"
import { splitFolderPath } from "./createFoldersFromGroups"

export type ScriptFolder = {
  name: string
  // Full path from the root (e.g. "a/b"), unique in the tree unlike the name
  path: string
  children: Array<ScriptFolder | ScriptBrief>
}

/**
 * Check if current object is a script (as opposed to a folder)
 */
export function isScript(x: ScriptFolder | ScriptBrief): x is ScriptBrief {
  return !("children" in x)
}

/**
 * Check if current object is a folder
 */
export function isScriptFolder(x: ScriptFolder | ScriptBrief): x is ScriptFolder {
  return "children" in x
}

/**
 * Sort folders before scripts; folders by name, scripts by name then by id.
 */
export function compareScriptFolderOrItem(
  a: ScriptFolder | ScriptBrief,
  b: ScriptFolder | ScriptBrief
) {
  const aIsFolder = isScriptFolder(a)
  const bIsFolder = isScriptFolder(b)

  if (aIsFolder !== bIsFolder) {
    return aIsFolder ? -1 : 1
  }

  if (aIsFolder && bIsFolder) {
    return a.name.localeCompare(b.name)
  }

  const script1 = a as ScriptBrief
  const script2 = b as ScriptBrief

  return script1.name.localeCompare(script2.name) || script1.id - script2.id
}

export function addScriptFolder(
  paths: string[],
  script: ScriptBrief,
  children: ScriptFolder["children"],
  parentPath = ""
) {
  const name = paths.shift()
  const path = parentPath ? `${parentPath}/${name}` : name
  let folder = children.find((f): f is ScriptFolder => isScriptFolder(f) && f.name === name)

  if (!folder) {
    folder = { name, path, children: [] } as ScriptFolder
    children.push(folder)
  }

  if (paths.length) {
    addScriptFolder(paths, script, folder.children, path)
  } else {
    folder.children.push(script)
  }

  return children.sort(compareScriptFolderOrItem)
}

/**
 * Create nested list of folders with scripts from netshot backend
 */
export function createFoldersFromScripts(scripts: ScriptBrief[] = []) {
  return scripts
    .reduce(
      (arr, script) => {
        const paths = splitFolderPath(script.folder)
        if (paths.length === 0) {
          arr.push(script)
        } else {
          addScriptFolder(paths, script, arr)
        }

        return arr
      },
      [] as Array<ScriptFolder | ScriptBrief>
    )
    .sort(compareScriptFolderOrItem)
}

interface FindResult {
  node: ScriptFolder | ScriptBrief
  path: ScriptFolder[]
}

export function findScriptNodeWithPath(
  tree: Array<ScriptFolder | ScriptBrief>,
  id: number,
  path: ScriptFolder[] = []
): FindResult | null {
  for (const node of tree) {
    if (isScript(node) && node.id === id) {
      return { node, path }
    }

    if (isScriptFolder(node)) {
      const result = findScriptNodeWithPath(node.children, id, [...path, node])
      if (result) return result
    }
  }

  return null
}
