import { Script } from "@/types"
import { splitFolderPath } from "./createFoldersFromGroups"

export type ScriptFolder = {
  name: string
  children: Array<ScriptFolder | Script>
}

/**
 * Check if current object is a script (as opposed to a folder)
 */
export function isScript(x: ScriptFolder | Script): x is Script {
  return !("children" in x)
}

/**
 * Check if current object is a folder
 */
export function isScriptFolder(x: ScriptFolder | Script): x is ScriptFolder {
  return "children" in x
}

/**
 * Sort folders before scripts; folders by name, scripts by name then by id.
 */
export function compareScriptFolderOrItem(
  a: ScriptFolder | Script,
  b: ScriptFolder | Script
) {
  const aIsFolder = isScriptFolder(a)
  const bIsFolder = isScriptFolder(b)

  if (aIsFolder !== bIsFolder) {
    return aIsFolder ? -1 : 1
  }

  if (aIsFolder && bIsFolder) {
    return a.name.localeCompare(b.name)
  }

  const script1 = a as Script
  const script2 = b as Script

  return script1.name.localeCompare(script2.name) || script1.id - script2.id
}

export function addScriptFolder(
  paths: string[],
  script: Script,
  children: ScriptFolder["children"]
) {
  const name = paths.shift()
  let folder = children.find((f): f is ScriptFolder => isScriptFolder(f) && f.name === name)

  if (!folder) {
    folder = { name, children: [] } as ScriptFolder
    children.push(folder)
  }

  if (paths.length) {
    addScriptFolder(paths, script, folder.children || (folder.children = []))
  } else {
    folder.children.push(script)
  }

  return children.sort(compareScriptFolderOrItem)
}

/**
 * Create nested list of folders with scripts from netshot backend
 */
export function createFoldersFromScripts(scripts: Script[] = []) {
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
      [] as Array<ScriptFolder | Script>
    )
    .sort(compareScriptFolderOrItem)
}

interface FindResult {
  node: ScriptFolder | Script
  path: ScriptFolder[]
}

export function findScriptNodeWithPath(
  tree: Array<ScriptFolder | Script>,
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
