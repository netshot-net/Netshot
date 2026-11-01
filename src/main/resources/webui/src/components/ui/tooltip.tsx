import { Tooltip as ChakraTooltip, Portal } from "@chakra-ui/react"
import * as React from "react"

export interface TooltipProps extends ChakraTooltip.RootProps {
  showArrow?: boolean
  portalled?: boolean
  portalRef?: React.RefObject<HTMLElement>
  content: React.ReactNode
  contentProps?: ChakraTooltip.ContentProps
  disabled?: boolean
  ref?: React.Ref<HTMLDivElement>
}

export function Tooltip(props: TooltipProps) {
  const {
    ref,
    showArrow,
    children,
    disabled,
    portalled = true,
    content,
    contentProps,
    portalRef,
    ...rest
  } = props

  if (disabled) return children

  // Props a parent injects into its child, such as Group's data-first/data-last/style that square
  // the corners of attached buttons, belong to the trigger element, not to the DOM-less Root.
  const rootProps: Record<string, unknown> = {}
  const triggerProps: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(rest)) {
    if (key.startsWith("data-") || key === "style") {
      triggerProps[key] = value
    } else {
      rootProps[key] = value
    }
  }

  return (
    <ChakraTooltip.Root {...rootProps}>
      <ChakraTooltip.Trigger asChild {...triggerProps}>
        {children}
      </ChakraTooltip.Trigger>
      <Portal disabled={!portalled} container={portalRef}>
        <ChakraTooltip.Positioner>
          <ChakraTooltip.Content ref={ref} {...contentProps}>
            {showArrow && (
              <ChakraTooltip.Arrow>
                <ChakraTooltip.ArrowTip />
              </ChakraTooltip.Arrow>
            )}
            {content}
          </ChakraTooltip.Content>
        </ChakraTooltip.Positioner>
      </Portal>
    </ChakraTooltip.Root>
  )
}
