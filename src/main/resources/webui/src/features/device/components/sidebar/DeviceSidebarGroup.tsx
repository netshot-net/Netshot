import { Protected } from "@/components"
import { AddGroupTrigger, TreeGroup } from "@/features/group"
import { Tooltip } from "@/components/ui/tooltip"
import { GroupBrief, Level } from "@/types"
import { Folder } from "@/utils"
import { Center, Flex, Heading, IconButton, Skeleton, Stack, Text } from "@chakra-ui/react"
import { LuPlus } from "react-icons/lu"
import { useTranslation } from "react-i18next"

export type DeviceSidebarGroupProps = {
  items: Array<Folder | GroupBrief>
  isPending: boolean
  selectedGroupId: number
  onGroupSelect(group: GroupBrief): void
  isFolderOpen(key: string): boolean
  toggleFolderOpen(key: string): void
}

export default function DeviceSidebarGroup(props: DeviceSidebarGroupProps) {
  const { items, isPending, selectedGroupId, onGroupSelect, isFolderOpen, toggleFolderOpen } = props
  const { t } = useTranslation()

  return (
    <Stack gap="0">
      <Flex justifyContent="space-between" alignItems="center" px="4" pt="3" pb="1">
        <Heading fontSize="md" fontWeight="medium">
          {t("group.list")}
        </Heading>
        <Protected minLevel={Level.ReadWrite}>
          <Tooltip content={t("group.add")}>
            <AddGroupTrigger>
              <IconButton variant="ghost" size="sm" aria-label={t("group.add")}>
                <LuPlus />
              </IconButton>
            </AddGroupTrigger>
          </Tooltip>
        </Protected>
      </Flex>
      <Stack px="4" pb="3" gap="0">
        {isPending ? (
          <Stack gap="3" pb="4">
            <Skeleton height="36px" />
            <Skeleton height="36px" />
            <Skeleton height="36px" />
            <Skeleton height="36px" />
          </Stack>
        ) : items.length === 0 ? (
          <Center pb="4">
            <Text color="fg.muted">{t("group.notFound")}</Text>
          </Center>
        ) : (
          <TreeGroup
            items={items}
            showMenu={true}
            onGroupSelect={onGroupSelect}
            isSelected={(group) => group.id === selectedGroupId}
            isFolderOpen={isFolderOpen}
            toggleFolderOpen={toggleFolderOpen}
          />
        )}
      </Stack>
    </Stack>
  )
}
