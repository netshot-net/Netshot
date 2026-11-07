import { GroupBrief } from "@/types"
import { createContext, JSX, PropsWithChildren, use } from "react"

export type TreeGroupContextProps = {
  isFolderOpen(key: string): boolean
  toggleFolderOpen(key: string): void
  showMenu?: boolean
  onGroupSelect?(group: GroupBrief): void
  isSelected?(group: GroupBrief): boolean
  renderGroupChildren?(group: GroupBrief): JSX.Element
}

export const TreeGroupContext = createContext<TreeGroupContextProps>(null!)

export const useTreeGroup = () => use(TreeGroupContext)

export function TreeGroupProvider({
  children,
  isFolderOpen,
  toggleFolderOpen,
  showMenu = false,
  onGroupSelect,
  isSelected,
  renderGroupChildren,
}: PropsWithChildren<TreeGroupContextProps>) {
  return (
    <TreeGroupContext
      value={{
        isFolderOpen,
        toggleFolderOpen,
        showMenu,
        onGroupSelect,
        isSelected,
        renderGroupChildren,
      }}
    >
      {children}
    </TreeGroupContext>
  )
}
