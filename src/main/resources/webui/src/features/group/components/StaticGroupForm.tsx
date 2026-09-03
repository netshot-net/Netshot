import api from "@/api"
import { useDebounce } from "@/hooks"
import { LightDevice } from "@/types"
import { DeviceListItem } from "@/components/entity"
import {
  Box,
  Center,
  type CollectionItem,
  Grid,
  Heading,
  IconButton,
  Input,
  InputGroup,
  Listbox,
  type ListboxRootProps,
  type ListboxValueChangeDetails,
  Stack,
  Text,
  useListCollection,
} from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { useEffect, useRef, useState, type ReactNode, type RefObject } from "react"
import { useFormContext, useWatch } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuArrowDown, LuArrowLeft, LuArrowRight, LuArrowUp, LuSearch } from "react-icons/lu"
import { GroupForm } from "../types"

type ListboxRenderProps<T extends CollectionItem> = {
  contentRef?: RefObject<HTMLDivElement>
  renderItem?: (item: T, itemValue: string, itemLabel: string) => ReactNode
  emptyMessage?: string
} & ListboxRootProps<T>

function ListboxRender<T extends CollectionItem>(props: ListboxRenderProps<T>) {
  const { collection, contentRef, renderItem, emptyMessage, ...rest } = props
  return (
    <Listbox.Root {...rest} collection={collection} selectionMode="multiple">
      <Listbox.Content minH="96" ref={contentRef}>
        {collection.items.length > 0 ? (
          collection.items.map((item) => {
            const itemValue = collection.getItemValue(item) ?? ""
            const itemLabel = collection.stringifyItem(item) ?? ""
            return (
              <Listbox.Item item={item} key={itemValue} flex="0">
                {renderItem ? (
                  renderItem(item, itemValue, itemLabel)
                ) : (
                  <Listbox.ItemText>{itemLabel}</Listbox.ItemText>
                )}
                <Listbox.ItemIndicator />
              </Listbox.Item>
            )
          })
        ) : (
          <Center boxSize="full" p="4" color="fg.muted" textStyle="sm">
            {emptyMessage}
          </Center>
        )}
      </Listbox.Content>
    </Listbox.Root>
  )
}

export default function StaticGroupForm() {
  const { t } = useTranslation()
  const form = useFormContext<GroupForm>()

  const [deviceQuery, setDeviceQuery] = useState("")
  const debouncedQuery = useDebounce(deviceQuery, 200)
  const [leftValues, setLeftValues] = useState<string[]>([])
  const [rightValues, setRightValues] = useState<string[]>([])
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set())
  const initializedRef = useRef(false)

  const formDevices = useWatch({ control: form.control, name: "staticDevices" })

  const leftCollection = useListCollection<LightDevice>({
    initialItems: [],
    itemToValue: (d) => d.id.toString(),
    itemToString: (d) => d.name,
  })

  const rightCollection = useListCollection<LightDevice>({
    initialItems: [],
    itemToValue: (d) => d.id.toString(),
    itemToString: (d) => d.name,
  })

  const { data: searchResult } = useQuery({
    queryKey: ["static-group:device:search", debouncedQuery],
    queryFn: () =>
      api.device.search({
        query: `[Name] containsnocase "${debouncedQuery}"`,
        limit: 20,
      }),
  })

  useEffect(() => {
    if (!initializedRef.current && (formDevices ?? []).length > 0) {
      initializedRef.current = true
      rightCollection.set(formDevices)
      setSelectedIds(new Set(formDevices.map((d) => d.id)))
    }
    // rightCollection is a fresh object every render (useListCollection isn't
    // memoized); adding it here would re-fire this effect on every render its
    // own .set() call causes, looping forever.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [formDevices])

  useEffect(() => {
    const filtered = (searchResult?.devices ?? []).filter((d) => !selectedIds.has(d.id))
    leftCollection.set(filtered)
    // leftCollection is a fresh object every render (useListCollection isn't
    // memoized); adding it here would re-fire this effect on every render its
    // own .set() call causes, looping forever.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [searchResult, selectedIds])

  function transferToRight() {
    const selected = leftCollection.collection.items.filter((item) =>
      leftValues.includes(leftCollection.collection.getItemValue(item) ?? "")
    )
    rightCollection.append(...selected)
    const newIds = new Set(selectedIds)
    selected.forEach((d) => newIds.add(d.id))
    setSelectedIds(newIds)
    form.setValue("staticDevices", [...rightCollection.collection.items, ...selected])
    setLeftValues([])
  }

  function transferToLeft() {
    const removedIdSet = new Set(
      rightCollection.collection.items
        .filter((item) => rightValues.includes(rightCollection.collection.getItemValue(item) ?? ""))
        .map((d) => d.id)
    )
    rightCollection.remove(...rightValues)
    const newIds = new Set(selectedIds)
    removedIdSet.forEach((id) => newIds.delete(id))
    setSelectedIds(newIds)
    form.setValue(
      "staticDevices",
      rightCollection.collection.items.filter((item) => !removedIdSet.has(item.id))
    )
    setRightValues([])
  }

  function moveSelected(direction: -1 | 1) {
    const items = [...rightCollection.collection.items]
    const selected = items.map((d) => rightValues.includes(d.id.toString()))
    if (direction === -1) {
      for (let i = 1; i < items.length; i++) {
        if (selected[i] && !selected[i - 1]) {
          ;[items[i - 1], items[i]] = [items[i], items[i - 1]]
          ;[selected[i - 1], selected[i]] = [selected[i], selected[i - 1]]
        }
      }
    } else {
      for (let i = items.length - 2; i >= 0; i--) {
        if (selected[i] && !selected[i + 1]) {
          ;[items[i + 1], items[i]] = [items[i], items[i + 1]]
          ;[selected[i + 1], selected[i]] = [selected[i], selected[i + 1]]
        }
      }
    }
    rightCollection.set(items)
    form.setValue("staticDevices", items)
  }

  return (
    <Stack flex="1" gap="5" overflow="auto">
      <Heading as="h4" size="md">
        {t("group.members")}
      </Heading>
      <Grid templateColumns="1fr auto 1fr" templateRows="auto 1fr auto" columnGap="4" rowGap="2" flex="1">
        <InputGroup gridColumn="1" gridRow="1" startElement={<LuSearch />}>
          <Input
            placeholder={t("device.search")}
            value={deviceQuery}
            onChange={(e) => setDeviceQuery(e.target.value)}
          />
        </InputGroup>
        <Box gridColumn="1" gridRow="2" borderWidth="1px" borderColor="grey.100" borderRadius="xl" overflow="hidden">
          <ListboxRender
            collection={leftCollection.collection}
            value={leftValues}
            onValueChange={(e: ListboxValueChangeDetails) => setLeftValues(e.value)}
            emptyMessage={deviceQuery ? t("device.noDeviceFound") : t("device.startTypingToFind")}
            renderItem={(device) => (
              <Listbox.ItemText>
                <DeviceListItem device={device} />
              </Listbox.ItemText>
            )}
          />
        </Box>
        <Text gridColumn="1" gridRow="3" textStyle="xs" color="fg.muted" px="3">
          {t("device.searchLimitNotice", { count: 20 })}
        </Text>

        <Stack gridColumn="2" gridRow="1 / -1" gap="2" alignItems="center" justifyContent="center">
          <IconButton
            variant="outline"
            size="sm"
            aria-label={t("common.add")}
            disabled={leftValues.length === 0}
            onClick={transferToRight}
          >
            <LuArrowRight />
          </IconButton>
          <IconButton
            variant="outline"
            size="sm"
            aria-label={t("common.remove")}
            disabled={rightValues.length === 0}
            onClick={transferToLeft}
          >
            <LuArrowLeft />
          </IconButton>
        </Stack>

        <Stack gridColumn="3" gridRow="1" direction="row" gap="2" justifyContent="flex-end">
          <IconButton
            variant="outline"
            size="sm"
            aria-label={t("common.moveUp")}
            disabled={rightValues.length === 0}
            onClick={() => moveSelected(-1)}
          >
            <LuArrowUp />
          </IconButton>
          <IconButton
            variant="outline"
            size="sm"
            aria-label={t("common.moveDown")}
            disabled={rightValues.length === 0}
            onClick={() => moveSelected(1)}
          >
            <LuArrowDown />
          </IconButton>
        </Stack>

        <Box gridColumn="3" gridRow="2" borderWidth="1px" borderColor="grey.100" borderRadius="xl" overflow="hidden">
          <ListboxRender
            collection={rightCollection.collection}
            value={rightValues}
            onValueChange={(e: ListboxValueChangeDetails) => setRightValues(e.value)}
            emptyMessage={t("device.noDevicesSelected")}
            renderItem={(device) => (
              <Listbox.ItemText>
                <DeviceListItem device={device} />
              </Listbox.ItemText>
            )}
          />
        </Box>
      </Grid>
    </Stack>
  )
}
