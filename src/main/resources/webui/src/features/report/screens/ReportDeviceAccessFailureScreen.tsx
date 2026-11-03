import api from "@/api"
import { DeviceAccessFailure } from "@/api/report"
import {
  EmptyResult,
  FormControl,
  Search,
  VirtualizedDataTable,
} from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { DomainSelect } from "@/features/administration/components"
import { DeviceBadge } from "@/components/entity"
import { LuFilter, LuFilterX, LuRefreshCcw } from "react-icons/lu"
import { FormControlType } from "@/components/FormControl"
import { useSearchQuery } from "@/hooks"
import { DeviceNetworkClass } from "@/types"
import { useLocalization } from "@/i18n"
import {
  Button,
  Heading,
  IconButton,
  Menu,
  Portal,
  Skeleton,
  Spacer,
  Stack,
  Text,
} from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { createColumnHelper } from "@tanstack/react-table"
import { useMemo, useState } from "react"
import { useForm, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router"
import { QUERIES } from "../constants"

type FilterForm = {
  domain: string | null
  days: number
}

const DEFAULT_FILTER: FilterForm = {
  domain: null,
  days: 7,
}

const columnHelper = createColumnHelper<DeviceAccessFailure>()

export default function ReportDeviceAccessFailure() {
  const { t } = useTranslation()
  const { formatDateTime } = useLocalization()
  const searchBox = useSearchQuery()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [filterOpen, setFilterOpen] = useState(false)
  const [filter, setFilter] = useState<FilterForm>(() => {
    const domain = searchParams.get("domain")
    const days = searchParams.get("days")
    return {
      domain: domain ?? DEFAULT_FILTER.domain,
      days: days ? +days : DEFAULT_FILTER.days,
    }
  })
  const form = useForm<FilterForm>({
    defaultValues: filter,
  })

  const isFiltered = Boolean(filter.domain) || filter.days !== DEFAULT_FILTER.days

  const formDays = useWatch({
    control: form.control,
    name: "days",
  })

  const {
    data = [],
    isPending,
    isFetching,
    refetch,
  } = useQuery({
    queryKey: [QUERIES.DEVICE_ACCESS_FAILURE, searchBox.query, filter.domain, filter.days],
    queryFn: async () =>
      api.report.getDeviceAccessFailures(
        filter.days,
        filter.domain ? +filter.domain : null,
        searchBox.query?.trim()
      ),
  })

  const isSearching = Boolean(searchBox.query?.trim())

  const columns = useMemo(
    () => [
      columnHelper.accessor("name", {
        cell: (info) => (
          <DeviceBadge networkClass={info.row.original.networkClass as DeviceNetworkClass | undefined}>
            <Link
              to={`/app/devices/${info.row.original.id}/general`}
              onClick={(e) => e.stopPropagation()}
            >
              {info.getValue()}
            </Link>
          </DeviceBadge>
        ),
        header: t("device.label"),
        size: 10000,
        enableSorting: true,
      }),
      columnHelper.accessor("mgmtAddress", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("device.managementIp"),
        size: 10000,
        enableSorting: true,
      }),
      columnHelper.accessor("family", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.family"),
        size: 15000,
        enableSorting: true,
      }),
      columnHelper.accessor("lastSnapshotSuccessDate", {
        cell: (info) => {
          const date = info.getValue()
          return <Text>{date ? formatDateTime(date) : t("common.nA")}</Text>
        },
        header: t("device.lastSuccessfulSnapshot"),
        size: 10000,
        enableSorting: true,
      }),
      columnHelper.accessor("lastSnapshotFailureDate", {
        cell: (info) => {
          const date = info.getValue()
          return <Text>{date ? formatDateTime(date) : t("common.nA")}</Text>
        },
        header: t("device.lastFailedSnapshot"),
        size: 10000,
        enableSorting: true,
      }),
    ],
    [t, formatDateTime]
  )

  function applyFilter(values: FilterForm) {
    setFilter(values)
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (values.domain) next.set("domain", values.domain)
        else next.delete("domain")
        if (values.days !== DEFAULT_FILTER.days) next.set("days", String(values.days))
        else next.delete("days")
        return next
      },
      { replace: true }
    )
    setFilterOpen(false)
  }

  function clearFilter() {
    setFilter(DEFAULT_FILTER)
    form.reset(DEFAULT_FILTER)
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.delete("domain")
        next.delete("days")
        return next
      },
      { replace: true }
    )
    setFilterOpen(false)
  }

  function navigateToDevice(row: DeviceAccessFailure) {
    navigate(`/app/devices/${row.id}/general`)
  }

  return (
    <>
      <Stack gap="6" p="9" flex="1" minH="0" overflow="hidden">
        <Stack direction="row" alignItems="center" gap="3">
          <Heading as="h1" fontSize="4xl">
            {t("device.accessFailures")}
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
        <Stack direction="row" gap="3">
          <Search
            placeholder={t("common.searchPlaceholder")}
            onQuery={searchBox.onQuery}
            onClear={searchBox.onQueryClear}
            w="30%"
          />
          <Spacer />
          <Menu.Root
            open={filterOpen}
            onOpenChange={(e) => {
              setFilterOpen(e.open)
              if (!e.open) {
                form.reset(filter)
              }
            }}
          >
            <Menu.Trigger asChild>
              <Button variant="primary">
                {isFiltered ? <LuFilterX /> : <LuFilter />}
                {t("common.filters")}
              </Button>
            </Menu.Trigger>
            <Portal>
              <Menu.Positioner>
                <Menu.Content w="300px" p="3">
                  <Stack gap="4" asChild>
                    <form onSubmit={form.handleSubmit(applyFilter)}>
                      <DomainSelect control={form.control} name="domain" withAny />
                      <FormControl
                        label={t("device.withoutSuccessfulSnapshotFor")}
                        type={FormControlType.Number}
                        size="sm"
                        control={form.control}
                        name="days"
                        suffix={
                          <Text color="grey.500" pr="4">
                            {t("time.day", { count: formDays })}
                          </Text>
                        }
                      />
                      <Stack direction="row" gap="2">
                        <Button type="button" flex="1" onClick={() => setFilterOpen(false)}>
                          {t("common.cancel")}
                        </Button>
                        <Button type="button" flex="1" onClick={clearFilter}>
                          {t("common.reset")}
                        </Button>
                        <Button type="submit" variant="primary" flex="1">
                          {t("common.apply")}
                        </Button>
                      </Stack>
                    </form>
                  </Stack>
                </Menu.Content>
              </Menu.Positioner>
            </Portal>
          </Menu.Root>
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
            {data.length > 0 ? (
              <VirtualizedDataTable
                zIndex={0}
                columns={columns}
                data={data}
                loading={isPending}
                onClickRow={navigateToDevice}
                primaryKey="id"
                flex="1"
                minH="0"
              />
            ) : isSearching ? (
              <Text>{t("common.noResults")}</Text>
            ) : (
              <EmptyResult
                title={t("device.noAccessFailure")}
                description={t("device.hereYouCanViewAccessFailureDate")}
              ></EmptyResult>
            )}
          </>
        )}
      </Stack>
    </>
  )
}
