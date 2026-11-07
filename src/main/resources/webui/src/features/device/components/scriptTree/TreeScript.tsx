import { ScriptBrief } from "@/types"
import { findScriptNodeWithPath, isScript, ScriptFolder } from "@/utils"
import { JSX } from "react"
import ScriptOrFolderItem from "./ScriptOrFolderItem"
import { TreeScriptProvider } from "./TreeScriptProvider"

type ScriptFolderOrItem = ScriptFolder | ScriptBrief

export type TreeScriptProps = {
  items: ScriptFolderOrItem[]
  onScriptSelect?(script: ScriptBrief): void
  onFolderSelect?(path: string): void
  isSelected?(script: ScriptBrief): boolean
  renderScriptChildren?(script: ScriptBrief): JSX.Element
  isFolderOpen(key: string): boolean
  toggleFolderOpen(key: string): void
  showMenu?: boolean
}

export function TreeScript({
  items,
  onScriptSelect,
  onFolderSelect,
  isSelected,
  renderScriptChildren,
  isFolderOpen,
  toggleFolderOpen,
  showMenu = false,
}: TreeScriptProps) {
  return (
    <TreeScriptProvider
      isFolderOpen={isFolderOpen}
      toggleFolderOpen={toggleFolderOpen}
      onScriptSelect={onScriptSelect}
      onFolderSelect={onFolderSelect}
      isSelected={isSelected}
      renderScriptChildren={renderScriptChildren}
      showMenu={showMenu}
    >
      {items?.map((item) => (
        <ScriptOrFolderItem item={item} key={isScript(item) ? item?.id : item?.name} />
      ))}
    </TreeScriptProvider>
  )
}

export function getExpandedKeys(items: ScriptFolderOrItem[], scriptId: number) {
  const result = findScriptNodeWithPath(items, scriptId)
  return result ? result.path.map((p) => p.path) : []
}
