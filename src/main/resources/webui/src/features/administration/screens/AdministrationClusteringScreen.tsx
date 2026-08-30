import api from "@/api"
import { EmptyResult, ExpandablePanel } from "@/components"
import IconBadge from "@/components/IconBadge"
import { Tooltip } from "@/components/ui/tooltip"
import { ClusterMemberStatus } from "@/types"
import { useLocalization } from "@/i18n"
import {
  Clipboard,
  Heading,
  Icon,
  IconButton,
  SimpleGrid,
  Skeleton,
  Stack,
  Text,
} from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { useMemo } from "react"
import { LuCopy, LuCopyCheck, LuHouse, LuRefreshCcw } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import ClusterStatusBadge from "../components/ClusterStatusBadge"
import ClusterSummary from "../components/ClusterSummary"
import { QUERIES } from "../constants"

const STATUS_ORDER: Record<ClusterMemberStatus, number> = {
  [ClusterMemberStatus.Master]: 0,
  [ClusterMemberStatus.Negotiating]: 1,
  [ClusterMemberStatus.Member]: 2,
  [ClusterMemberStatus.Expired]: 3,
}

export default function AdministrationClusteringScreen() {
  const { t } = useTranslation()
  const { formatDateTime, formatRelativeTime } = useLocalization()

  const {
    data = [],
    isPending,
    isFetching,
    refetch,
  } = useQuery({
    queryKey: [QUERIES.ADMIN_CLUSTERS],
    queryFn: async () => (await api.admin.getAllClusterMembers()) ?? [],
  })

  const members = useMemo(() => {
    return [...data].sort((a, b) => {
      const order = STATUS_ORDER[a.status] - STATUS_ORDER[b.status]
      return order !== 0 ? order : a.hostname.localeCompare(b.hostname)
    })
  }, [data])

  return (
    <Stack gap="6" p="9" flex="1" overflow="auto">
      <Stack gap="1">
        <Stack direction="row" alignItems="center" gap="3">
          <Heading as="h1" fontSize="4xl">
            {t("admin.clustering.label")}
          </Heading>
          <Tooltip content={t("common.refresh")}>
            <IconButton
              aria-label={t("common.refresh")}
              variant="ghost"
              size="sm"
              color="fg.muted"
              onClick={() => refetch()}
              loading={isFetching}
            >
              <LuRefreshCcw />
            </IconButton>
          </Tooltip>
        </Stack>
        <Text fontSize="sm" color="grey.400">
          {t("admin.clustering.description")}
        </Text>
      </Stack>

      {!isPending && data.length > 0 && <ClusterSummary members={data} />}

      <Heading as="h2" fontSize="2xl" fontWeight="semibold">
        {t("admin.clustering.memberList")}
      </Heading>

      {isPending ? (
        <Stack gap="3">
          <Skeleton h="60px"></Skeleton>
          <Skeleton h="60px"></Skeleton>
          <Skeleton h="60px"></Skeleton>
          <Skeleton h="60px"></Skeleton>
        </Stack>
      ) : (
        <>
          {members.length > 0 ? (
            <Stack gap="3">
              {members.map((item) => {
                const isExpired = item.status === ClusterMemberStatus.Expired

                return (
                  <ExpandablePanel
                    key={item.instanceId}
                    borderColor={isExpired ? "red.200" : undefined}
                    opacity={isExpired ? 0.7 : 1}
                  >
                    <ExpandablePanel.Header>
                      <Stack direction="row" justifyContent="space-between" alignItems="center" flex="1">
                        <Stack direction="row" gap="3" alignItems="center">
                          <Text fontWeight="semibold" fontSize="lg">
                            {item?.hostname}
                          </Text>
                          <ClusterStatusBadge status={item.status} />
                          {item?.local && (
                            <IconBadge colorPalette="gray">
                              <Icon size="sm" flexShrink={0}>
                                <LuHouse />
                              </Icon>
                              {t("user.localType")}
                            </IconBadge>
                          )}
                        </Stack>
                        <Tooltip
                          content={item.lastSeenTime ? formatDateTime(item.lastSeenTime) : undefined}
                          disabled={!item.lastSeenTime}
                        >
                          <Text color="grey.400" fontSize="sm">
                            {item.lastSeenTime
                              ? t("admin.clustering.lastSeenAgo", { time: formatRelativeTime(item.lastSeenTime) })
                              : "—"}
                          </Text>
                        </Tooltip>
                      </Stack>
                    </ExpandablePanel.Header>
                    <ExpandablePanel.Content>
                      <SimpleGrid columns={{ base: 2, md: 3, lg: 5 }} gap="6" flex="1">
                        <Stack gap="0">
                          <Text color="grey.400">{t("admin.clustering.instanceId")}</Text>
                          <Text fontFamily="mono" fontWeight="semibold" fontSize="lg">
                            {item?.instanceId}
                          </Text>
                        </Stack>
                        <Stack gap="0">
                          <Text color="grey.400">{t("about.version")}</Text>
                          <Text fontWeight="semibold" fontSize="lg">
                            {item?.appVersion}
                          </Text>
                        </Stack>
                        <Stack gap="0">
                          <Text color="grey.400">{t("admin.clustering.clusteringVersion")}</Text>
                          <Text fontWeight="semibold" fontSize="lg">
                            {item?.clusteringVersion}
                          </Text>
                        </Stack>
                        <Stack gap="0" gridColumn="span 2">
                          <Text color="grey.400">{t("admin.clustering.jvmVersion")}</Text>
                          <Text fontWeight="semibold" fontSize="lg">
                            {item?.jvmVersion}
                          </Text>
                        </Stack>
                        <Stack gap="0">
                          <Text color="grey.400">{t("admin.clustering.masterPriority")}</Text>
                          <Text fontWeight="semibold" fontSize="lg">
                            {item?.masterPriority}
                          </Text>
                        </Stack>
                        <Stack gap="0">
                          <Text color="grey.400">{t("task.runnerPriority")}</Text>
                          <Text fontWeight="semibold" fontSize="lg">
                            {item?.runnerPriority}
                          </Text>
                        </Stack>
                        <Stack gap="0">
                          <Text color="grey.400">{t("task.runnerWeight")}</Text>
                          <Text fontWeight="semibold" fontSize="lg">
                            {item?.runnerWeight}
                          </Text>
                        </Stack>
                        <Stack gap="0">
                          <Text color="grey.400">{t("admin.clustering.driverHash")}</Text>
                          <Stack direction="row" alignItems="center" gap="1">
                            <Tooltip content={item?.driverHash}>
                              <Text fontWeight="semibold" fontSize="lg" cursor="default">
                                {item?.driverHash?.substring(0, 8)}
                              </Text>
                            </Tooltip>
                            <Clipboard.Root value={item?.driverHash}>
                              <Clipboard.Trigger asChild>
                                <IconButton size="xs" variant="ghost" aria-label={t("common.copy")}>
                                  <Clipboard.Indicator copied={<LuCopyCheck />}>
                                    <LuCopy />
                                  </Clipboard.Indicator>
                                </IconButton>
                              </Clipboard.Trigger>
                            </Clipboard.Root>
                          </Stack>
                        </Stack>
                        <Stack gap="0" gridColumn="span 2">
                          <Text color="grey.400">{t("compliance.lastStatusChange")}</Text>
                          <Text fontWeight="semibold" fontSize="lg">
                            {formatDateTime(item?.lastStatusChangeTime)}
                          </Text>
                        </Stack>
                      </SimpleGrid>
                    </ExpandablePanel.Content>
                  </ExpandablePanel>
                )
              })}
            </Stack>
          ) : (
            <EmptyResult
              title={t("compliance.hardware.noMember")}
              description={t("admin.clustering.activateHint")}
            />
          )}
        </>
      )}
    </Stack>
  )
}
