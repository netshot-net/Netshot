import { Tooltip } from "@/components/ui/tooltip"
import { Heading, IconButton, Stack } from "@chakra-ui/react"
import { useTranslation } from "react-i18next"
import { LuRefreshCcw } from "react-icons/lu"
import { ConfigurationChangeList, ConfigurationChart } from "../components"
import { useConfigChangeBins, useConfigChanges } from "../hooks"

export default function ReportConfigurationChangeScreen() {
  const { t } = useTranslation()
  const changes = useConfigChanges()
  const bins = useConfigChangeBins()
  const isFetching = changes.isFetching || bins.isFetching

  function refetch() {
    changes.refetch()
    bins.refetch()
  }

  return (
    <Stack gap="6" p="9" flex="1" minH="0" overflow="hidden">
      <Stack direction="row" alignItems="center" gap="3">
        <Heading as="h1" fontSize="4xl">
          {t("device.config.changes")}
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
      <ConfigurationChart />
      <ConfigurationChangeList />
    </Stack>
  )
}
