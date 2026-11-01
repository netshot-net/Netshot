import { Protected } from "@/components"
import { Icon } from "@chakra-ui/react"
import { LuSquareStack, LuSquarePen, LuTrash, LuEllipsis } from "react-icons/lu"
import { GroupBrief, Level } from "@/types"
import {
  Box,
  BoxProps,
  Flex,
  IconButton,
  Menu,
  Portal,
  Spacer,
  Stack,
  Text,
} from "@chakra-ui/react"
import { MouseEvent, useEffect, useRef } from "react"
import { useTranslation } from "react-i18next"
import EditGroupTrigger from "./EditGroupTrigger"
import RemoveGroupTrigger from "./RemoveGroupTrigger"
import { useTreeGroup } from "./TreeGroupProvider"

export type GroupItemProps = {
  group: GroupBrief
} & BoxProps

export default function GroupItem(props: GroupItemProps) {
  const { group, ...other } = props

  const { t } = useTranslation()
  const ctx = useTreeGroup()

  const selected = ctx.isSelected ? ctx.isSelected(group) : false

  const itemRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (selected && itemRef.current) {
      itemRef.current.scrollIntoView({ block: "nearest" })
    }
  }, [selected])

  function select(evt: MouseEvent<HTMLDivElement>) {
    if (!ctx.onGroupSelect) {
      return
    }

    evt?.stopPropagation()
    ctx.onGroupSelect(group)
  }

  return (
    <Box ref={itemRef} {...other}>
      <Flex
        borderRadius="xl"
        transition="all .2s ease"
        cursor="pointer"
        pl="2"
        ml="-2"
        minHeight="40px"
        alignItems="center"
        _hover={{
          bg: selected ? "green.50" : "grey.50",
        }}
        bg={selected ? "green.50" : "white"}
        onClick={select}
      >
        <Stack direction="row" gap="3" alignItems="center">
          <Stack direction="row" gap="3" alignItems="center">
            <Icon color="green.600" size="md"><LuSquareStack /></Icon>
            <Text>{group?.name}</Text>
          </Stack>
        </Stack>
        <Spacer />
        {ctx.renderGroupChildren?.(group)}
        {ctx.showMenu && (
          <Protected minLevel={Level.ReadWrite}>
            <Menu.Root>
              <Menu.Trigger asChild>
                <IconButton
                  variant="frame"
                  size="sm"
                  aria-label={t("common.openGroupOptions")}
                  onClick={(e) => e.stopPropagation()}
                >
                  <LuEllipsis />
                </IconButton>
              </Menu.Trigger>
              <Portal>
                <Menu.Positioner>
                  <Menu.Content onClick={(e) => e.stopPropagation()}>
                    <EditGroupTrigger group={group}>
                      <Menu.Item value="item-0">
                        <LuSquarePen />
                        {t("common.edit")}
                      </Menu.Item>
                    </EditGroupTrigger>
                    <RemoveGroupTrigger group={group}>
                      <Menu.Item value="item-1">
                        <LuTrash />
                        {t("common.remove")}
                      </Menu.Item>
                    </RemoveGroupTrigger>
                  </Menu.Content>
                </Menu.Positioner>
              </Portal>
            </Menu.Root>
          </Protected>
        )}
      </Flex>
    </Box>
  )
}
