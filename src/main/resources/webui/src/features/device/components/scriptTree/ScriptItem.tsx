import { Protected } from "@/components"
import { Icon } from "@chakra-ui/react"
import { LuFileTerminal, LuTrash, LuEllipsis } from "react-icons/lu"
import { Level, Script } from "@/types"
import {
  Box,
  BoxProps,
  Flex,
  IconButton,
  Menu,
  Portal,
  Spacer,
  Stack,
  Tag,
  Text,
} from "@chakra-ui/react"
import { MouseEvent, useEffect, useRef } from "react"
import { useTranslation } from "react-i18next"
import RemoveScriptTrigger from "./RemoveScriptTrigger"
import { useTreeScript } from "./TreeScriptProvider"

export type ScriptItemProps = {
  script: Script
} & BoxProps

export default function ScriptItem(props: ScriptItemProps) {
  const { script, ...other } = props

  const { t } = useTranslation()
  const ctx = useTreeScript()

  const selected = ctx.isSelected ? ctx.isSelected(script) : false

  const itemRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (selected && itemRef.current) {
      itemRef.current.scrollIntoView({ block: "nearest" })
    }
  }, [selected])

  function select(evt: MouseEvent<HTMLDivElement>) {
    if (!ctx.onScriptSelect) {
      return
    }

    evt?.stopPropagation()
    ctx.onScriptSelect(script)
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
          <Icon color="green.600" size="md">
            <LuFileTerminal />
          </Icon>
          <Text>{script?.name}</Text>
          <Stack direction="row" gap="2">
            <Tag.Root size="sm" colorPalette="grey">
              {script?.deviceDriverDescription}
            </Tag.Root>
            <Tag.Root size="sm" colorPalette="green">
              {script?.author}
            </Tag.Root>
          </Stack>
        </Stack>
        <Spacer />
        {ctx.renderScriptChildren?.(script)}
        {ctx.showMenu && (
          <Protected minLevel={Level.ExecureReadWrite}>
            <Menu.Root>
              <Menu.Trigger asChild>
                <IconButton
                  variant="frame"
                  size="sm"
                  aria-label={t("script.openOptions")}
                  onClick={(e) => e.stopPropagation()}
                >
                  <LuEllipsis />
                </IconButton>
              </Menu.Trigger>
              <Portal>
                <Menu.Positioner>
                  <Menu.Content onClick={(e) => e.stopPropagation()}>
                    <RemoveScriptTrigger script={script}>
                      <Menu.Item value="item-0">
                        <LuTrash />
                        {t("common.remove")}
                      </Menu.Item>
                    </RemoveScriptTrigger>
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
