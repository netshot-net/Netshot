import { ClusterMember, ClusterMemberStatus } from "@/types"
import { Box, Icon, SimpleGrid, Stack, Text } from "@chakra-ui/react"
import { useMemo } from "react"
import { LuCrown, LuFingerprint, LuServer, LuTriangleAlert } from "react-icons/lu"
import { useTranslation } from "react-i18next"

export type ClusterSummaryProps = {
  members: ClusterMember[]
}

function Tile(props: { icon: React.ReactElement; color: string; label: string; value: React.ReactNode }) {
  const { icon, color, label, value } = props

  return (
    <Box p="4" borderRadius="xl" borderWidth="1px" borderColor="grey.100" bg="white" flex="1">
      <Stack direction="row" alignItems="flex-start" gap="3">
        <Icon boxSize="6" color={color} flexShrink={0}>
          {icon}
        </Icon>
        <Stack flex="1" gap="0">
          <Text fontSize="sm" fontWeight="medium" color="grey.700">
            {label}
          </Text>
          <Text fontSize="md" fontWeight="bold" truncate>
            {value}
          </Text>
        </Stack>
      </Stack>
    </Box>
  )
}

export default function ClusterSummary(props: ClusterSummaryProps) {
  const { members } = props
  const { t } = useTranslation()

  const stats = useMemo(() => {
    const master = members.find((member) => member.status === ClusterMemberStatus.Master)
    const expiredCount = members.filter((member) => member.status === ClusterMemberStatus.Expired).length
    const distinctHashes = new Set(members.map((member) => member.driverHash))

    return {
      total: members.length,
      master,
      expiredCount,
      distinctHashCount: distinctHashes.size,
    }
  }, [members])

  return (
    <SimpleGrid columns={4} gap="4">
      <Tile
        icon={<LuServer />}
        color="grey.400"
        label={t("admin.clustering.members")}
        value={stats.total}
      />
      <Tile
        icon={<LuCrown />}
        color={stats.master ? "green.500" : "orange.500"}
        label={t("admin.clustering.master")}
        value={stats.master ? stats.master.hostname : t("admin.clustering.noMaster")}
      />
      <Tile
        icon={<LuTriangleAlert />}
        color={stats.expiredCount > 0 ? "red.500" : "grey.400"}
        label={t("admin.clustering.expired")}
        value={stats.expiredCount}
      />
      <Tile
        icon={<LuFingerprint />}
        color={stats.distinctHashCount > 1 ? "orange.500" : "green.500"}
        label={t("admin.clustering.driverConsistency")}
        value={
          stats.distinctHashCount > 1
            ? t("admin.clustering.hashMismatch", { count: stats.distinctHashCount })
            : t("admin.clustering.hashConsistent")
        }
      />
    </SimpleGrid>
  )
}
