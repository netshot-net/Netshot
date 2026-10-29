import { Icon } from "@chakra-ui/react"
import { LuFolder, LuFolderOpen } from "react-icons/lu"
import { Folder, isGroup } from "@/utils"
import { Box, BoxProps, Flex, Stack, Text } from "@chakra-ui/react"
import { motion, useAnimationControls } from "framer-motion"
import { MouseEvent, useCallback, useEffect } from "react"
import GroupOrFolderItem from "./GroupOrFolderItem"
import { useTreeGroup } from "./TreeGroupProvider"

export type FolderItemProps = {
  folder: Folder
} & BoxProps

export default function FolderItem(props: FolderItemProps) {
  const { folder, ...other } = props

  const ctx = useTreeGroup()
  const controls = useAnimationControls()
  const isOpen = ctx.isFolderOpen(folder.path)

  function toggleCollapse(evt?: MouseEvent) {
    evt?.stopPropagation()
    ctx.toggleFolderOpen(folder.path)
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
              <GroupOrFolderItem
                pl="4"
                item={child}
                key={isGroup(child) ? child?.id : child?.name}
              />
            ))}
          </Stack>
        </motion.div>
      </Stack>
    </Box>
  )
}
