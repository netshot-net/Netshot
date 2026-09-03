import api, { DeviceSearchResult } from "@/api"
import DeviceBadge from "@/components/entity/DeviceBadge"
import DeviceListItem from "@/components/entity/DeviceListItem"
import { LightDevice } from "@/types"
import {
  Box,
  BoxProps,
  Combobox,
  ComboboxRootProps,
  Group,
  IconButton,
  Portal,
  Text,
  useComboboxContext,
} from "@chakra-ui/react"
import { cloneElement, ReactElement } from "react"
import { useTranslation } from "react-i18next"
import { LuX } from "react-icons/lu"
import { useAutocomplete, WithFilterBy } from "@/components/autocomplete"

function DeviceComboboxInput({ placeholder }: { placeholder?: string }) {
  const combobox = useComboboxContext()
  const items = combobox.selectedItems as LightDevice[]
  const device = items[0]

  if (!device) {
    return <Combobox.Input placeholder={placeholder} />
  }

  return (
    <>
      <Box
        position="absolute"
        inset="0"
        display="flex"
        alignItems="center"
        ps="var(--combobox-input-padding-x)"
        pe="var(--combobox-input-padding-end)"
        pointerEvents="none"
      >
        <DeviceBadge networkClass={device.networkClass}>{device.name}</DeviceBadge>
      </Box>
      <Combobox.Input
        style={{ color: "transparent", caretColor: "transparent", pointerEvents: "none" }}
      />
    </>
  )
}

export type DeviceAutocompleteProps = Omit<ComboboxRootProps, "collection"> & {
  placeholder?: string
  onSelectItem?(item: LightDevice | null): void
  // Rendered attached to the end of the combobox (e.g. a "run" button). Group attaches
  // it internally so the radius reset can target the actual bordered input part.
  endAddon?: ReactElement<{ h?: BoxProps["h"]; w?: BoxProps["w"] }>
} & WithFilterBy<LightDevice>

export default function DeviceAutocomplete(props: DeviceAutocompleteProps) {
  const {
    filterBy,
    placeholder,
    onSelectItem,
    endAddon,
    selectionBehavior = "clear",
    ...comboboxProps
  } = props

  const { t } = useTranslation()

  const {
    entities,
    query,
    isPending,
    isError,
    collection,
    onInputValueChange,
    inputValue,
  } = useAutocomplete<LightDevice, DeviceSearchResult>({
    initialItems: [],
    itemToString: (item) => item.name,
    itemToValue: (item) => item.id.toString(),
    queryKey: ["autocomplete:device"],
    async queryFn(query) {
      const result = await api.device.search({
        query: `[Name] containsnocase "${query}"`,
        limit: 20,
      })
      return result ?? { query, devices: [] }
    },
    select(data) {
      return data.devices
    },
    filterBy,
  })

  const combobox = (
    <Combobox.Root
      collection={collection}
      size="lg"
      openOnClick
      selectionBehavior={selectionBehavior}
      closeOnSelect
      onInputValueChange={onInputValueChange}
      inputValue={inputValue}
      onValueChange={(evt) => {
        const device = entities.find((item) => item.id === +evt.value[0])
        onSelectItem?.(device ?? null)
      }}
      {...comboboxProps}
      // When wrapped by the Group below, it clones data-first/data-last/data-between onto
      // us - the actual bordered box is the nested input part, so the radius reset needs
      // its own scoped selector rather than relying on Group's own (too shallow) CSS.
      css={{
        "&[data-first] [data-part=input]": { borderEndRadius: "0" },
        "&[data-between] [data-part=input]": { borderRadius: "0" },
        "&[data-last] [data-part=input]": { borderStartRadius: "0" },
      }}
    >
      <Combobox.Control>
        <DeviceComboboxInput placeholder={placeholder || t("device.search")} />
        <Combobox.IndicatorGroup>
          <Combobox.Context>
            {(ctx) => (
              <>
                {(ctx.inputValue || ctx.value.length > 0) && (
                  <Combobox.ClearTrigger asChild>
                    <IconButton size="xs" variant="ghost" borderRadius="xl">
                      <LuX />
                    </IconButton>
                  </Combobox.ClearTrigger>
                )}
                {!(ctx.value.length > 0) && <Combobox.Trigger />}
              </>
            )}
          </Combobox.Context>
        </Combobox.IndicatorGroup>
      </Combobox.Control>

      <Portal>
        <Combobox.Positioner>
          <Combobox.Content>
            {isPending ? (
              <Text>{t("common.loading")}</Text>
            ) : isError ? (
              <Text>{t("common.anErrorHasOccurred")}</Text>
            ) : (
              <>
                <Combobox.Empty>
                  <Text>{t(query?.length > 0 ? "device.noDeviceFound" : "device.startTypingToFind")}</Text>
                </Combobox.Empty>
                {collection.items.map((item) => (
                  <Combobox.Item item={item} key={item.id}>
                    <DeviceListItem device={item} />
                    <Combobox.ItemIndicator />
                  </Combobox.Item>
                ))}
              </>
            )}
          </Combobox.Content>
        </Combobox.Positioner>
      </Portal>
    </Combobox.Root>
  )

  if (!endAddon) return combobox

  return (
    <Group attached>
      {combobox}
      {/* h/w "12" matches the combobox's own size="lg" height (--combobox-input-height: sizes.12) */}
      {/* eslint-disable-next-line @eslint-react/no-clone-element -- one-off size override on a caller-supplied addon element, narrowly typed via ReactElement<{ h, w }> */}
      {cloneElement(endAddon, { h: "12", w: "12" })}
    </Group>
  )
}
