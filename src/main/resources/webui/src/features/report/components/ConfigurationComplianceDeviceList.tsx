import api from "@/api"
import { EmptyResult, Search } from "@/components"
import { QUERIES } from "@/constants"
import { useSearchQuery } from "@/hooks"
import { ComplianceResult } from "@/api/report"
import { GroupBrief } from "@/types"
import { Skeleton, Spacer, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { useVirtualizer } from "@tanstack/react-virtual"
import { useCallback, useMemo, useRef } from "react"
import { useTranslation } from "react-i18next"
import DeviceConfigurationCompliancePanel from "./DeviceConfigurationCompliancePanel"

export type ConfigurationComplianceDeviceListProps = {
  group: GroupBrief
}

export default function ConfigurationComplianceDeviceList(
  props: ConfigurationComplianceDeviceListProps
) {
  const { group } = props

  const { t } = useTranslation()
  const searchBox = useSearchQuery()
  const containerRef = useRef<HTMLDivElement>(null)

  const { data, isPending } = useQuery({
    queryKey: [QUERIES.DEVICE_REPORT_GROUP_LIST, group.id, group.folder, group.name],
    queryFn: async () => api.report.getNonCompliantResults(group.id),
    select: useCallback(
      (res: ComplianceResult[]): Map<number, ComplianceResult[]> => {
        const text = (searchBox.query ?? "").trim().toLowerCase()
        const byDevice = new Map<number, ComplianceResult[]>()
        for (const result of res) {
          if (!result.device || (text && !(result.device.name ?? "").toLowerCase().includes(text))) {
            continue
          }
          byDevice.set(result.device.id, [...(byDevice.get(result.device.id) ?? []), result])
        }
        return byDevice
      },
      [searchBox.query]
    ),
  })

  const rows = useMemo(() => Array.from(data?.entries() ?? []), [data])
  const isSearching = Boolean(searchBox.query?.trim())

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => containerRef.current,
    estimateSize: () => 67,
    measureElement: (element) => element?.getBoundingClientRect().height,
    overscan: 5,
  })

  return (
    <Stack gap="3" flex="1" minH="0">
      <Stack direction="row">
        <Search
          placeholder={t("common.searchPlaceholder")}
          onQuery={searchBox.onQuery}
          onClear={searchBox.onQueryClear}
          w="30%"
        />
        <Spacer />
      </Stack>
      {isPending ? (
        <Stack gap="3">
          <Skeleton h="60px"></Skeleton>
          <Skeleton h="60px"></Skeleton>
          <Skeleton h="60px"></Skeleton>
          <Skeleton h="60px"></Skeleton>
        </Stack>
      ) : (
        <>
          {rows.length > 0 ? (
            <Stack ref={containerRef} flex="1" minH="0" overflow="auto" gap="0">
              <div
                style={{
                  height: `${virtualizer.getTotalSize()}px`,
                  width: "100%",
                  position: "relative",
                  flexShrink: 0,
                }}
              >
                {virtualizer.getVirtualItems().map((virtualItem) => {
                  const [deviceId, results] = rows[virtualItem.index]

                  return (
                    <div
                      key={deviceId}
                      ref={virtualizer.measureElement}
                      data-index={virtualItem.index}
                      style={{
                        position: "absolute",
                        top: 0,
                        left: 0,
                        width: "100%",
                        paddingBottom: "12px",
                        transform: `translateY(${virtualItem.start}px)`,
                      }}
                    >
                      <DeviceConfigurationCompliancePanel results={results} />
                    </div>
                  )
                })}
              </div>
            </Stack>
          ) : isSearching ? (
            <Text>{t("common.noResults")}</Text>
          ) : (
            <EmptyResult
              title={t("device.noDevice")}
              description={t("compliance.noNonCompliantDeviceInGroup")}
            />
          )}
        </>
      )}
    </Stack>
  )
}
