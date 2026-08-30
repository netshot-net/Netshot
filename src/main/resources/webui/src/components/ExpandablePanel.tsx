import { LuChevronDown } from "react-icons/lu"
import { IconButton, Separator, Stack, StackProps } from "@chakra-ui/react"
import { LegacyAnimationControls, Variants, motion, useAnimationControls } from "framer-motion"
import { PropsWithChildren, createContext, use, useCallback, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"

const ExpandablePanelContext = createContext<{
  toggle(): void
  isCollapsed: boolean
  controls: LegacyAnimationControls
}>(null!)

const useExpandablePanel = () => use(ExpandablePanelContext)

function Header(props: PropsWithChildren) {
  const { children } = props
  const { t } = useTranslation()
  const ctx = useExpandablePanel()
  const transform = useMemo(() => (ctx.isCollapsed ? "rotate(-90deg)" : ""), [ctx])

  return (
    <Stack direction="row" gap="3" alignItems="center" p="3" onClick={ctx.toggle} cursor="pointer">
      <IconButton
        size="sm"
        variant="ghost"
        colorPalette="green"
        aria-label={t("common.open")}
        transform={transform}
      >
        <LuChevronDown />
      </IconButton>
      {children}
    </Stack>
  )
}

function Content(props: PropsWithChildren) {
  const { children } = props
  const ctx = useExpandablePanel()
  const variants = useMemo(
    () =>
      ({
        hidden: { opacity: 0, height: 0, pointerEvents: "none" },
        show: {
          opacity: 1,
          height: "auto",
          pointerEvents: "all",
        },
      }) as Variants,
    []
  )

  const transition = useMemo(
    () => ({
      duration: 0.2,
    }),
    []
  )

  return (
    <motion.div initial="hidden" animate={ctx.controls} variants={variants} transition={transition}>
      <Separator />
      <Stack direction="row" gap="3" p="6">
        {children}
      </Stack>
    </motion.div>
  )
}

function ExpandablePanel(props: PropsWithChildren<StackProps>) {
  const { children, ...other } = props
  const controls = useAnimationControls()
  const [isCollapsed, setIsCollapsed] = useState<boolean>(true)

  const toggle = useCallback(async () => {
    setIsCollapsed((prev) => !prev)
    await controls.start(isCollapsed ? "show" : "hidden")
  }, [controls, isCollapsed])

  const ctx = useMemo(
    () => ({
      toggle,
      isCollapsed,
      controls,
    }),
    [toggle, isCollapsed, controls]
  )

  return (
    <ExpandablePanelContext value={ctx}>
      <Stack borderWidth="1px" borderColor="grey.100" borderRadius="2xl" gap="0" {...other}>
        {children}
      </Stack>
    </ExpandablePanelContext>
  )
}

export default Object.assign(ExpandablePanel, {
  Header,
  Content,
})
