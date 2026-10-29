import { Icon } from "@chakra-ui/react"
import { LuFolder, LuFolderOpen } from "react-icons/lu"
import { isScript, ScriptFolder } from "@/utils"
import { Box, BoxProps, Flex, Stack, Text } from "@chakra-ui/react"
import { motion, useAnimationControls } from "framer-motion"
import { MouseEvent, useCallback, useEffect } from "react"
import ScriptOrFolderItem from "./ScriptOrFolderItem"
import { useTreeScript } from "./TreeScriptProvider"

export type ScriptFolderItemProps = {
  folder: ScriptFolder
} & BoxProps

export default function ScriptFolderItem(props: ScriptFolderItemProps) {
  const { folder, ...other } = props

  const ctx = useTreeScript()
  const controls = useAnimationControls()
  const isOpen = ctx.isFolderOpen(folder.path)

  function toggleCollapse(evt?: MouseEvent) {
    evt?.stopPropagation()
    ctx.toggleFolderOpen(folder.path)
    ctx.onFolderSelect?.(folder.path)
  }

  const runAnimation = useCallback(async () => {
    await controls.start(isOpen ? "show" : "hidden")
  }, [controls, isOpen])

  useEffect(() => {
    runAnimation()
  }, [isOpen, runAnimation])

  return (
    <Box {...other}>
      <Stack direction="column" gap="0" onClick={toggleCollapse}>
        <Flex
          borderRadius="xl"
          transition="all .2s ease"
          pl="2"
          ml="-2"
          cursor="pointer"
          h="40px"
          alignItems="center"
          _hover={{
            bg: "grey.50",
          }}
        >
          <Stack direction="row" gap="3" alignItems="center">
            <Icon color="green.600" size="md">
              {isOpen ? <LuFolderOpen /> : <LuFolder />}
            </Icon>
            <Text>{folder?.name}</Text>
          </Stack>
        </Flex>
        <motion.div
          initial="hidden"
          animate={controls}
          style={{ overflow: "hidden" }}
          variants={{
            hidden: { opacity: 0, height: 0, pointerEvents: "none" },
            show: {
              opacity: 1,
              height: "auto",
              pointerEvents: "all",
            },
          }}
          transition={{
            duration: 0.2,
          }}
        >
          <Stack direction="column" gap="0">
            {folder?.children?.map((child) => (
              <ScriptOrFolderItem
                pl="4"
                item={child}
                key={isScript(child) ? child?.id : child?.name}
              />
            ))}
          </Stack>
        </motion.div>
      </Stack>
    </Box>
  )
}
