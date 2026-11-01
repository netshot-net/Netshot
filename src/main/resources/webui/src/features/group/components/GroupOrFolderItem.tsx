import { GroupBrief } from "@/types"
import { Folder, isGroup } from "@/utils"
import { BoxProps } from "@chakra-ui/react";
import { PropsWithChildren } from "react"
import FolderItem from "./FolderItem"
import GroupItem from "./GroupItem"

export type GroupOrFolderItemProps = {
  item: GroupBrief | Folder
} & BoxProps

export default function GroupOrFolderItem(
  props: PropsWithChildren<GroupOrFolderItemProps>
) {
  const { item, ...other } = props

  if (isGroup(item)) {
    return <GroupItem group={item} {...other} />
  }

  return <FolderItem folder={item} {...other} />
}
