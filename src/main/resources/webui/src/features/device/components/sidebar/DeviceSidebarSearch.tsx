import { QueryBuilderTrigger } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { IconButton, Input, InputGroup, Popover, Portal, Stack } from "@chakra-ui/react"
import { ChangeEvent, KeyboardEvent, useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { LuCompass, LuSearch, LuX } from "react-icons/lu"
import { useLocation, useNavigate, useSearchParams } from "react-router"
import { useShallow } from "zustand/react/shallow"
import DeviceGroupBadge from "@/components/entity/DeviceGroupBadge"
import { useDeviceSidebarStore } from "../../stores"
import DeviceSidebarGroup from "./DeviceSidebarGroup"
import { useDeviceGroupTree } from "./useDeviceGroupTree"

const IPV4 =
  "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"
const MASK_V4 = "/([0-9]|1[0-9]|2[0-9]|3[0-2])"
const IPV6 =
  "((([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){6}:[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){5}:([0-9A-Fa-f]{1,4}:)?[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){4}:([0-9A-Fa-f]{1,4}:){0,2}[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){3}:([0-9A-Fa-f]{1,4}:){0,3}[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){2}:([0-9A-Fa-f]{1,4}:){0,4}[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){6}((\\b((25[0-5])|(1\\d{2})|(2[0-4]\\d)|(\\d{1,2}))\\b)\\.){3}(\\b((25[0-5])|(1\\d{2})|(2[0-4]\\d)|(\\d{1,2}))\\b))|(([0-9A-Fa-f]{1,4}:){0,5}:((\\b((25[0-5])|(1\\d{2})|(2[0-4]\\d)|(\\d{1,2}))\\b)\\.){3}(\\b((25[0-5])|(1\\d{2})|(2[0-4]\\d)|(\\d{1,2}))\\b))|(::([0-9A-Fa-f]{1,4}:){0,5}((\\b((25[0-5])|(1\\d{2})|(2[0-4]\\d)|(\\d{1,2}))\\b)\\.){3}(\\b((25[0-5])|(1\\d{2})|(2[0-4]\\d)|(\\d{1,2}))\\b))|([0-9A-Fa-f]{1,4}::([0-9A-Fa-f]{1,4}:){0,5}[0-9A-Fa-f]{1,4})|(::([0-9A-Fa-f]{1,4}:){0,6}[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){1,7}:))"
const MASK_V6 = "/([0-9]|[0-9][0-9]|1[01][0-9]|12[0-8])"

function buildQuery(text: string): string {
  const trimmed = text.trim()
  if (trimmed.startsWith("[")) return trimmed

  if (
    new RegExp(`^${IPV4}${MASK_V4}$`).test(trimmed) ||
    new RegExp(`^${IPV6}${MASK_V6}$`).test(trimmed)
  ) {
    return `[IP] in ${trimmed}`
  }
  if (new RegExp(`^${IPV4}$`).test(trimmed) || new RegExp(`^${IPV6}$`).test(trimmed)) {
    return `[IP] is ${trimmed}`
  }
  return `[Name] containsnocase "${trimmed.replace(/"/g, '\\"')}"`
}

export default function DeviceSidebarSearch() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()

  const { query, driver, group, deselectAll, setQuery, setGroup, updateQueryAndDriver } =
    useDeviceSidebarStore(
      useShallow((state) => ({
        query: state.query,
        driver: state.driver,
        group: state.group,
        deselectAll: state.deselectAll,
        setQuery: state.setQuery,
        setGroup: state.setGroup,
        updateQueryAndDriver: state.updateQueryAndDriver,
      }))
    )

  const [text, setText] = useState<string>(() => searchParams.get("q") ?? "")
  const [isOpen, setIsOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const { items, isPending, selectedGroupId, onGroupSelect, isFolderOpen, toggleFolderOpen } =
    useDeviceGroupTree({
      enabled: isOpen,
      onAfterSelect: () => setIsOpen(false),
    })

  const q = searchParams.get("q")
  // Sentinel distinct from any possible searchParams value, so the hydration
  // branch below always runs once on mount (even when q is already set).
  const [prevQ, setPrevQ] = useState<string | null | undefined>(undefined)
  const [prevGroup, setPrevGroup] = useState(group)

  // Hydrate the search box text from "?q=" (initial load, browser back/forward,
  // or a link such as a device tag badge), without the extra render an
  // effect-based sync would cost.
  if (q !== prevQ) {
    setPrevQ(q)
    if (q) setText(q)
  }

  // The query itself lives in the (external) sidebar store, shared with other
  // components: it must not be written during render (React would warn about
  // updating a component while rendering a different one), so sync it here.
  useEffect(() => {
    if (q) setQuery(buildQuery(q))
  }, [q, setQuery])

  // Selecting a group clears any text/advanced search shown in the box
  if (group !== prevGroup) {
    setPrevGroup(group)
    if (group) setText("")
  }

  function runSearch(value: string) {
    deselectAll()
    setGroup(null)

    if (!value.trim()) {
      updateQueryAndDriver("", null)
      navigate({ pathname: location.pathname, search: undefined }, { replace: true })
      return
    }

    setQuery(buildQuery(value))
    navigate(
      { pathname: location.pathname, search: `?q=${encodeURIComponent(value)}` },
      { replace: true }
    )
  }

  function handleChange(evt: ChangeEvent<HTMLInputElement>) {
    if (group) {
      setGroup(null)
      navigate({ pathname: location.pathname, search: undefined }, { replace: true })
    }
    setText(evt.target.value)
  }

  function handleKeyDown(evt: KeyboardEvent<HTMLInputElement>) {
    if (evt.key !== "Enter") return
    runSearch(text)
    setIsOpen(false)
  }

  function handleClear() {
    setText("")
    setGroup(null)
    updateQueryAndDriver("", null)
    setQuery("")
    navigate({ pathname: location.pathname, search: undefined }, { replace: true })
    inputRef.current?.focus()
  }

  return (
    <Stack p="6" gap="5">
      <Popover.Root
        open={isOpen}
        onOpenChange={(details) => setIsOpen(details.open)}
        autoFocus={false}
        positioning={{ placement: "bottom" }}
      >
        <Popover.Anchor>
          <InputGroup
            startElement={
              group ? (
                <DeviceGroupBadge id={group.id} name={group.name} maxW="150px" />
              ) : (
                <LuSearch size={18} />
              )
            }
            endElement={
              <>
                <Tooltip content={t("common.queryBuilder")}>
                  <QueryBuilderTrigger
                    value={{ query, driver: driver ?? "" }}
                    onSubmit={(res) => {
                      setGroup(null)
                      updateQueryAndDriver(res.query, res.driver)
                      setText(res.query ?? "")
                      navigate(
                        {
                          pathname: location.pathname,
                          search: res.query ? `?q=${encodeURIComponent(res.query)}` : undefined,
                        },
                        { replace: true }
                      )
                      setIsOpen(false)
                    }}
                  >
                    <IconButton size="xs" variant="ghost" aria-label={t("common.openQueryBuilder")}>
                      <LuCompass />
                    </IconButton>
                  </QueryBuilderTrigger>
                </Tooltip>
                {(Boolean(text) || Boolean(group)) && (
                  <IconButton size="xs" variant="ghost" aria-label={t("common.clear")} onClick={handleClear}>
                    <LuX />
                  </IconButton>
                )}
              </>
            }
          >
            <Input
              ref={inputRef}
              variant="outline"
              value={group ? "" : text}
              onFocus={() => setIsOpen(true)}
              onClick={() => setIsOpen(true)}
              onChange={handleChange}
              onKeyDown={handleKeyDown}
              placeholder={group ? "" : t("device.searchOrGroupPlaceholder")}
              pe="70px"
            />
          </InputGroup>
        </Popover.Anchor>

        <Portal>
          <Popover.Positioner>
            <Popover.Content w="260px" overflow="auto">
              <DeviceSidebarGroup
                items={items}
                isPending={isPending}
                selectedGroupId={selectedGroupId}
                onGroupSelect={onGroupSelect}
                isFolderOpen={isFolderOpen}
                toggleFolderOpen={toggleFolderOpen}
              />
            </Popover.Content>
          </Popover.Positioner>
        </Portal>
      </Popover.Root>
    </Stack>
  )
}
