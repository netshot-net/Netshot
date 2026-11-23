import { BadgeProps, Icon } from "@chakra-ui/react"
import { type Ref } from "react"
import {
  LuBan,
  LuCircleDashed,
  LuCircleDot,
  LuCirclePlay,
  LuClock,
  LuCircleCheck,
  LuCircleX,
  LuHourglass,
} from "react-icons/lu"
import { useTranslation } from "react-i18next"
import { TaskStatus } from "@/types"
import IconBadge from "@/components/IconBadge"
import { tKey, type TranslationKey } from "@/i18n/tKey"

type Config = {
  colorPalette: string
  icon: React.ReactElement
  labelKey: TranslationKey
}

export const TASK_STATUS_CONFIG: Record<TaskStatus, Config> = {
  [TaskStatus.New]: {
    colorPalette: "gray",
    icon: <LuCircleDot />,
    labelKey: tKey("common.new"),
  },
  [TaskStatus.Delayed]: {
    colorPalette: "gray",
    icon: <LuHourglass />,
    labelKey: tKey("common.delayed"),
  },
  [TaskStatus.Scheduled]: {
    colorPalette: "yellow",
    icon: <LuClock />,
    labelKey: tKey("common.scheduled"),
  },
  [TaskStatus.Waiting]: {
    colorPalette: "gray",
    icon: <LuCircleDashed />,
    labelKey: tKey("common.waiting"),
  },
  [TaskStatus.Running]: {
    colorPalette: "blue",
    icon: <LuCirclePlay />,
    labelKey: tKey("common.running"),
  },
  [TaskStatus.Success]: {
    colorPalette: "green",
    icon: <LuCircleCheck />,
    labelKey: tKey("common.success"),
  },
  [TaskStatus.Failure]: {
    colorPalette: "red",
    icon: <LuCircleX />,
    labelKey: tKey("common.failure"),
  },
  [TaskStatus.Cancelled]: {
    colorPalette: "gray",
    icon: <LuBan />,
    labelKey: tKey("common.cancelled"),
  },
}

type TaskStatusBadgeProps = BadgeProps & {
  status: TaskStatus
  ref?: Ref<HTMLSpanElement>
}

function TaskStatusBadge({ status, ref, ...rest }: TaskStatusBadgeProps) {
  const { t } = useTranslation()
  const config = TASK_STATUS_CONFIG[status]

  if (!config) return null

  return (
    <IconBadge ref={ref} colorPalette={config.colorPalette} {...rest}>
      <Icon size="sm" flexShrink={0}>{config.icon}</Icon>
      {t(config.labelKey)}
    </IconBadge>
  )
}

export default TaskStatusBadge
