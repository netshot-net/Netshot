import api from "@/api"
import { EmptyResult, Search } from "@/components"
import { DomainSelect } from "@/features/administration/components"
import { useTreeOpenKeys } from "@/hooks"
import { Tooltip } from "@/components/ui/tooltip"
import { QUERIES as GLOBAL_QUERIES } from "@/constants"
import { useCustomDialog } from "@/dialog"
import { DeviceSoftwareLevelBadge } from "@/components/entity"
import { usePagination } from "@/hooks"
import { DeviceSoftwareLevel, Group, GroupSoftwareComplianceStat } from "@/types"
import { createFoldersFromGroups, Folder, isGroup, search } from "@/utils"
import {
  Box,
  Button,
  Flex,
  Grid,
  Heading,
  Icon,
  IconButton,
  Menu,
  Portal,
  Skeleton,
  Spacer,
  Stack,
  StackProps,
  Text,
} from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { MouseEvent, useCallback, useMemo, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuFilter, LuFilterX, LuFolder, LuFolderOpen, LuRefreshCcw, LuSquareStack } from "react-icons/lu"
import { useNavigate, useSearchParams } from "react-router"
import SoftwareComplianceDialog from "../components/SoftwareComplianceDialog"
import { QUERIES } from "../constants"

type FilterForm = {
  domain: number | null
}

const DEFAULT_FILTER: FilterForm = {
  domain: null,
}

// Left indent per nesting depth, mirroring the shared tree component's own recursive
// indentation (kept independent from it since this screen renders its own flat grid).
const DEPTH_INDENT_PX = 20
const BASE_INDENT_PX = 8

type FolderRow = {
  kind: "folder"
  key: string
  depth: number
  folder: Folder
}

type GroupRow = {
  kind: "group"
  key: string
  depth: number
  group: Group
}

type TreeRow = FolderRow | GroupRow

function flattenTree(
  items: (Group | Folder)[],
  depth: number,
  isFolderOpen: (key: string) => boolean
): TreeRow[] {
  const rows: TreeRow[] = []

  for (const item of items) {
    if (isGroup(item)) {
      rows.push({ kind: "group", key: `group-${item.id}`, depth, group: item })
    } else {
      rows.push({ kind: "folder", key: `folder-${item.path}`, depth, folder: item })
      if (isFolderOpen(item.path)) {
        rows.push(...flattenTree(item.children, depth + 1, isFolderOpen))
      }
    }
  }

  return rows
}

type LevelStatProps = StackProps & {
  level: DeviceSoftwareLevel
  count: number
  onClick(evt: MouseEvent): void
}

// Each of these renders as its own grid cell. Since every row of the tree shares the
// same grid, the browser sizes each level's column to fit the widest cell across ALL
// rows at once (`max-content` on the grid's templateColumns) -- columns stay aligned
// and nothing gets clipped, with no manual width bookkeeping needed. The grid has no
// columnGap of its own (see below): each cell carries its own px so that, once the
// row's shared hover background lights up every cell at once, adjacent cells' boxes
// touch with no visible seam, reading as one continuous bar.
function LevelStat(props: LevelStatProps) {
  const { level, count, onClick, ...rest } = props

  return (
    <Stack
      direction="row"
      alignItems="center"
      gap="2"
      h="40px"
      px="3"
      cursor="pointer"
      onClick={onClick}
      {...rest}
    >
      <DeviceSoftwareLevelBadge level={level} />
      <Text color="fg.muted">{count}</Text>
    </Stack>
  )
}

type FolderGridRowProps = {
  row: FolderRow
  isOpen: boolean
  onToggle(key: string): void
}

function FolderGridRow(props: FolderGridRowProps) {
  const { row, isOpen, onToggle } = props

  return (
    <Flex
      gridColumn="1 / -1"
      borderRadius="xl"
      h="40px"
      pl={`${BASE_INDENT_PX + row.depth * DEPTH_INDENT_PX}px`}
      alignItems="center"
      gap="3"
      cursor="pointer"
      transition="background .2s ease"
      _hover={{ bg: "grey.50" }}
      onClick={() => onToggle(row.folder.path)}
    >
      <Icon color="green.600" size="md">
        {isOpen ? <LuFolderOpen /> : <LuFolder />}
      </Icon>
      <Text>{row.folder.name}</Text>
    </Flex>
  )
}

type GroupGridRowProps = {
  row: GroupRow
  item: GroupSoftwareComplianceStat
  onGroupSelect(group: Group): void
}

function GroupGridRow(props: GroupGridRowProps) {
  const { row, item, onGroupSelect } = props
  const dialog = useCustomDialog()

  const nonCompliantDeviceCount = useMemo(
    () => item.deviceCount - item.goldDeviceCount - item.silverDeviceCount - item.bronzeDeviceCount,
    [item]
  )

  const openLevel = useCallback(
    (level: DeviceSoftwareLevel) => (evt: MouseEvent) => {
      evt.stopPropagation()
      dialog.open(
        <SoftwareComplianceDialog groupId={item.groupId} groupName={item.groupName} level={level} />
      )
    },
    [dialog, item]
  )

  return (
    <Box as="div" display="contents" role="group">
      <Flex
        pl={`${BASE_INDENT_PX + row.depth * DEPTH_INDENT_PX}px`}
        pr="3"
        alignItems="center"
        gap="3"
        h="40px"
        minW="0"
        borderLeftRadius="xl"
        transition="background .2s ease"
        _groupHover={{ bg: "grey.50" }}
      >
        <Icon color="green.600" size="md">
          <LuSquareStack />
        </Icon>
        <Text
          cursor="pointer"
          transition="color .2s ease"
          _hover={{ color: "green.600" }}
          overflow="hidden"
          textOverflow="ellipsis"
          whiteSpace="nowrap"
          onClick={() => onGroupSelect(row.group)}
        >
          {row.group.name}
        </Text>
      </Flex>
      <LevelStat
        level={DeviceSoftwareLevel.GOLD}
        count={item.goldDeviceCount}
        onClick={openLevel(DeviceSoftwareLevel.GOLD)}
        transition="background .2s ease"
        _groupHover={{ bg: "grey.50" }}
      />
      <LevelStat
        level={DeviceSoftwareLevel.SILVER}
        count={item.silverDeviceCount}
        onClick={openLevel(DeviceSoftwareLevel.SILVER)}
        transition="background .2s ease"
        _groupHover={{ bg: "grey.50" }}
      />
      <LevelStat
        level={DeviceSoftwareLevel.BRONZE}
        count={item.bronzeDeviceCount}
        onClick={openLevel(DeviceSoftwareLevel.BRONZE)}
        transition="background .2s ease"
        _groupHover={{ bg: "grey.50" }}
      />
      <LevelStat
        level={DeviceSoftwareLevel.NON_COMPLIANT}
        count={nonCompliantDeviceCount}
        onClick={openLevel(DeviceSoftwareLevel.NON_COMPLIANT)}
        borderRightRadius="xl"
        transition="background .2s ease"
        _groupHover={{ bg: "grey.50" }}
      />
    </Box>
  )
}

const EMPTY_STAT: Omit<GroupSoftwareComplianceStat, "groupId" | "groupName"> = {
  deviceCount: 0,
  goldDeviceCount: 0,
  silverDeviceCount: 0,
  bronzeDeviceCount: 0,
}

export default function ReportSoftwareComplianceScreen() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const pagination = usePagination()
  const [searchParams, setSearchParams] = useSearchParams()
  const [filterOpen, setFilterOpen] = useState(false)
  const [filter, setFilter] = useState<FilterForm>(() => {
    const domainParam = searchParams.get("domain")
    return { domain: domainParam ? +domainParam : DEFAULT_FILTER.domain }
  })
  const form = useForm<FilterForm>({ defaultValues: filter })

  const isFiltered = filter.domain != null

  const { data: stats, isPending, isFetching, refetch } = useQuery({
    queryKey: [QUERIES.SOFTWARE_COMPLIANCE, filter.domain],
    queryFn: async () =>
      api.report.getAllGroupSoftwareComplianceStats(filter.domain ? { domain: filter.domain } : {}),
    select(res) {
      // Groups with no device, or flagged as hidden from reports (already excluded server-side),
      // must not appear in the tree.
      return (res ?? []).filter((item) => item.deviceCount > 0)
    },
  })

  const { data: groups, isPending: isGroupPending } = useQuery({
    queryKey: [GLOBAL_QUERIES.DEVICE_GROUPS],
    queryFn: async () => {
      return api.group.getAll()
    },
  })

  const treeItems = useMemo(() => {
    const nonEmptyGroupIds = new Set((stats ?? []).map((stat) => stat.groupId))
    const visibleGroups = (groups ?? []).filter((group) => nonEmptyGroupIds.has(group.id))
    return createFoldersFromGroups(search(visibleGroups, "name").with(pagination.query))
  }, [groups, stats, pagination.query])

  function applyFilter(values: FilterForm) {
    setFilter(values)
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (values.domain != null) next.set("domain", String(values.domain))
        else next.delete("domain")
        return next
      },
      { replace: true }
    )
    setFilterOpen(false)
  }

  function resetFilter() {
    setFilter(DEFAULT_FILTER)
    form.reset(DEFAULT_FILTER)
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.delete("domain")
        return next
      },
      { replace: true }
    )
    setFilterOpen(false)
  }

  const { isOpen, toggle } = useTreeOpenKeys()

  const onGroupSelect = useCallback(
    (group: Group) => {
      navigate(`/app/devices?group=${group.id}`)
    },
    [navigate]
  )

  const rows = useMemo(() => flattenTree(treeItems, 0, isOpen), [treeItems, isOpen])
  const isSearching = Boolean(pagination.query?.trim())

  return (
    <Stack gap="8" p="9" flex="1" overflow="auto">
      <Stack direction="row" alignItems="center" gap="3">
        <Heading as="h1" fontSize="4xl">
          {t("compliance.software.versionLabel")}
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
          onQuery={pagination.onQuery}
          onClear={pagination.onQueryClear}
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
                    <Stack direction="row" gap="2">
                      <Button type="button" flex="1" onClick={() => setFilterOpen(false)}>
                        {t("common.cancel")}
                      </Button>
                      <Button type="button" flex="1" onClick={resetFilter}>
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
      <Stack flex="1" overflow="auto">
        {isGroupPending && isPending ? (
          <Stack gap="3" pb="6">
            <Skeleton height="36px" />
            <Skeleton height="36px" />
            <Skeleton height="36px" />
            <Skeleton height="36px" />
          </Stack>
        ) : rows.length > 0 ? (
          <Grid templateColumns="minmax(200px, 1fr) repeat(4, max-content)" columnGap="0" rowGap="0">
            {rows.map((row) => {
              if (row.kind === "folder") {
                return <FolderGridRow key={row.key} row={row} isOpen={isOpen(row.folder.path)} onToggle={toggle} />
              }

              const item = stats?.find((stat) => stat.groupId === row.group.id) ?? {
                groupId: row.group.id,
                groupName: row.group.name,
                ...EMPTY_STAT,
              }

              return <GroupGridRow key={row.key} row={row} item={item} onGroupSelect={onGroupSelect} />
            })}
          </Grid>
        ) : isSearching ? (
          <Text>{t("common.noResults")}</Text>
        ) : (
          <EmptyResult
            title={t("group.notFound")}
            description={t("group.complianceStatusDesc")}
          />
        )}
      </Stack>
    </Stack>
  )
}
