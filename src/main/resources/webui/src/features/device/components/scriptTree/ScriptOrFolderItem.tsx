import { ScriptBrief } from "@/types"
import { isScript, ScriptFolder } from "@/utils"
import { BoxProps } from "@chakra-ui/react"
import { PropsWithChildren } from "react"
import ScriptFolderItem from "./ScriptFolderItem"
import ScriptItem from "./ScriptItem"

export type ScriptOrFolderItemProps = {
  item: ScriptBrief | ScriptFolder
} & BoxProps

export default function ScriptOrFolderItem(
  props: PropsWithChildren<ScriptOrFolderItemProps>
) {
  const { item, ...other } = props

  if (isScript(item)) {
    return <ScriptItem script={item} {...other} />
  }

  return <ScriptFolderItem folder={item} {...other} />
}
