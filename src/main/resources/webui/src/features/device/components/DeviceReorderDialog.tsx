import { useDialogConfig } from "@/dialog"
import { Device, SimpleDevice } from "@/types"
import {
  Box,
  Button,
  CloseButton,
  Dialog,
  Heading,
  Icon,
  IconButton,
  Portal,
  Stack,
} from "@chakra-ui/react"
import { useVirtualizer } from "@tanstack/react-virtual"
import { useRef, useState } from "react"
import { LuArrowDown, LuArrowUp, LuCheck } from "react-icons/lu"
import { useTranslation } from "react-i18next"
import DeviceListItem from "@/components/entity/DeviceListItem"

export type DeviceReorderDialogProps = {
  devices: (SimpleDevice | Device)[]
  onSave(devices: (SimpleDevice | Device)[]): void
}

export default function DeviceReorderDialog(props: DeviceReorderDialogProps) {
  const { devices, onSave } = props
  const { t } = useTranslation()
  const dialogConfig = useDialogConfig()
  const containerRef = useRef<HTMLDivElement>(null)

  const [items, setItems] = useState(devices)
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set())

  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => containerRef.current,
    estimateSize: () => 44,
    overscan: 10,
  })

  function toggleSelected(id: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  function move(direction: -1 | 1) {
    const items_ = [...items]
    const selected = items_.map((device) => selectedIds.has(device.id))

    if (direction === -1) {
      for (let i = 1; i < items_.length; i++) {
        if (selected[i] && !selected[i - 1]) {
          ;[items_[i - 1], items_[i]] = [items_[i], items_[i - 1]]
          ;[selected[i - 1], selected[i]] = [selected[i], selected[i - 1]]
        }
      }
    } else {
      for (let i = items_.length - 2; i >= 0; i--) {
        if (selected[i] && !selected[i + 1]) {
          ;[items_[i + 1], items_[i]] = [items_[i], items_[i + 1]]
          ;[selected[i + 1], selected[i]] = [selected[i], selected[i + 1]]
        }
      }
    }

    setItems(items_)
  }

  function handleSave() {
    onSave(items)
    dialogConfig.close()
  }

  return (
    <Dialog.Root
      scrollBehavior="inside"
      size="sm"
      open={dialogConfig.props.isOpen}
      closeOnEscape={false}
      closeOnInteractOutside={false}
      onOpenChange={(e) => {
        if (!e.open) {
          dialogConfig.close()
        }
      }}
      onExitComplete={() => {
        dialogConfig.remove()
      }}
    >
      <Portal>
        <Dialog.Backdrop />
        <Dialog.Positioner>
          <Dialog.Content height="50vh">
            <Dialog.Header pr="12">
              <Heading as="h3" fontSize="xl" fontWeight="semibold">
                {t("device.devices")} ({items.length})
              </Heading>
            </Dialog.Header>
            <Dialog.Body overflow="hidden" display="flex" flexDirection="column" flex="1" minH="0">
              <Stack direction="row" gap="3" flex="1" minH="0" alignItems="stretch" overflow="hidden">
                <Stack gap="2" alignItems="center" justifyContent="center">
                  <IconButton
                    variant="outline"
                    size="sm"
                    aria-label={t("common.moveUp")}
                    disabled={selectedIds.size === 0}
                    onClick={() => move(-1)}
                  >
                    <LuArrowUp />
                  </IconButton>
                  <IconButton
                    variant="outline"
                    size="sm"
                    aria-label={t("common.moveDown")}
                    disabled={selectedIds.size === 0}
                    onClick={() => move(1)}
                  >
                    <LuArrowDown />
                  </IconButton>
                </Stack>
                <Box
                  ref={containerRef}
                  flex="1"
                  minH="0"
                  overflowY="auto"
                  position="relative"
                  borderWidth="1px"
                  borderColor="grey.100"
                  borderRadius="xl"
                  bg="bg.panel"
                  p="1"
                >
                  <div
                    style={{
                      height: `${virtualizer.getTotalSize()}px`,
                      width: "100%",
                      position: "relative",
                    }}
                  >
                    {virtualizer.getVirtualItems().map((virtualItem) => {
                      const device = items[virtualItem.index]
                      const selected = selectedIds.has(device.id)

                      return (
                        <Box
                          key={device.id}
                          position="absolute"
                          top="0"
                          left="0"
                          w="100%"
                          h={`${virtualItem.size}px`}
                          style={{
                            transform: `translateY(${virtualItem.start}px)`,
                          }}
                        >
                          <Stack
                            direction="row"
                            alignItems="center"
                            gap="2"
                            h="calc(100% - 4px)"
                            my="2px"
                            px="2"
                            cursor="pointer"
                            borderRadius="lg"
                            bg={selected ? "green.50" : undefined}
                            color={selected ? "green.800" : undefined}
                            _hover={{ bg: "green.50", color: "green.800" }}
                            onClick={() => toggleSelected(device.id)}
                          >
                            <Box flex="1" minW="0">
                              <DeviceListItem device={device} />
                            </Box>
                            {selected && (
                              <Icon color="green.700">
                                <LuCheck size={16} />
                              </Icon>
                            )}
                          </Stack>
                        </Box>
                      )
                    })}
                  </div>
                </Box>
              </Stack>
            </Dialog.Body>
            <Dialog.Footer>
              <Button variant="default" onClick={() => dialogConfig.close()}>
                {t("common.cancel")}
              </Button>
              <Button variant="primary" onClick={handleSave}>
                {t("common.save")}
              </Button>
            </Dialog.Footer>
            <Dialog.CloseTrigger asChild>
              <CloseButton size="sm" variant="outline" />
            </Dialog.CloseTrigger>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  )
}
