import { BadgeProps, Icon } from "@chakra-ui/react"
import { type Ref } from "react"
import { LuCircleX, LuClock, LuCrown, LuServer } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import { ClusterMemberStatus } from "@/types"
import IconBadge from "@/components/IconBadge"

type Config = {
  colorPalette: string
  icon: React.ReactElement
  labelKey: string
}

export const CLUSTER_STATUS_CONFIG: Record<ClusterMemberStatus, Config> = {
  [ClusterMemberStatus.Master]: {
    colorPalette: "green",
    icon: <LuCrown />,
    labelKey: "admin.clustering.master",
  },
  [ClusterMemberStatus.Member]: {
    colorPalette: "blue",
    icon: <LuServer />,
    labelKey: "admin.clustering.member",
  },
  [ClusterMemberStatus.Negotiating]: {
    colorPalette: "yellow",
    icon: <LuClock />,
    labelKey: "admin.clustering.negotiating",
  },
  [ClusterMemberStatus.Expired]: {
    colorPalette: "red",
    icon: <LuCircleX />,
    labelKey: "admin.clustering.expired",
  },
}

type ClusterStatusBadgeProps = BadgeProps & {
  status: ClusterMemberStatus
  ref?: Ref<HTMLSpanElement>
}

function ClusterStatusBadge({ status, ref, ...rest }: ClusterStatusBadgeProps) {
  const { t } = useTranslation()
  const config = CLUSTER_STATUS_CONFIG[status]

  if (!config) return null

  return (
    <IconBadge ref={ref} colorPalette={config.colorPalette} {...rest}>
      <Icon size="sm" flexShrink={0}>{config.icon}</Icon>
      {t(config.labelKey)}
    </IconBadge>
  )
}

export default ClusterStatusBadge
