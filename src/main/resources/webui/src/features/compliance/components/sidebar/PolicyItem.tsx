import type { PolicyWithRules } from "@/api/policy"
import { Icon, Stack, Text } from "@chakra-ui/react"
import { Tooltip } from "@/components/ui/tooltip"
import { LuCircleX, LuFolder, LuFolderOpen } from "react-icons/lu"
import { motion, useAnimationControls } from "framer-motion"
import { useCallback, useEffect, useMemo, useRef } from "react"
import { useNavigate, useParams } from "react-router"
import { useComplianceSidebar } from "../../contexts/ComplianceSidebarProvider"
import RuleItem from "./RuleItem"

export type PolicyItemProps = {
  policy: PolicyWithRules
  forceExpand?: boolean
}

export default function PolicyItem(props: PolicyItemProps) {
  const { policy, forceExpand } = props
  const { policyId, ruleId } = useParams()
  const navigate = useNavigate()
  const ctx = useComplianceSidebar()
  const controls = useAnimationControls()
  const hasRules = useMemo(() => (policy.rules?.length ?? 0) > 0, [policy])
  const isAssigned = useMemo(() => (policy.targetGroups?.length ?? 0) > 0, [policy])
  const isActive = useMemo(() => !!policyId && +policyId === policy.id, [policyId, policy])
  const isPolicySelected = useMemo(() => isActive && !ruleId, [isActive, ruleId])
  const isCollapsed = forceExpand ? false : !ctx.isPolicyExpanded(policy.id)

  const policyRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (isPolicySelected && policyRef.current) {
      policyRef.current.scrollIntoView({ block: "nearest" })
    }
  }, [isPolicySelected])

  useEffect(() => {
    if (isActive && hasRules) {
      ctx.setPolicyExpanded(policy.id, true)
    }
    // Only react to route/data changes, expansion is driven by user toggles otherwise.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [isActive, hasRules, policy.id])

  useEffect(() => {
    controls.start(isCollapsed ? "hidden" : "show")
  }, [isCollapsed, controls])

  const handleClick = useCallback(async () => {
    navigate(`./config/${policy.id}`)
    if (!hasRules || forceExpand) return
    if (isPolicySelected) {
      ctx.togglePolicyExpanded(policy.id)
    } else if (isCollapsed) {
      ctx.setPolicyExpanded(policy.id, true)
    }
  }, [navigate, policy.id, hasRules, forceExpand, isCollapsed, isPolicySelected, ctx])

  return (
    <Stack gap="0" mx="-3" ref={policyRef}>
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        borderRadius="lg"
        bg={isPolicySelected ? "green.50" : "white"}
        height="36px"
        transition="all .2s ease"
        _hover={{
          bg: isPolicySelected ? "green.50" : "grey.50",
        }}
        onClick={handleClick}
        userSelect="none"
        cursor="pointer"
        px="3"
      >
        <Stack direction="row" gap="3" alignItems="center" title={policy?.name ?? undefined} overflow="hidden">
          <Icon color="green.600" size="md" flexShrink="0">
            {isCollapsed ? <LuFolder /> : <LuFolderOpen />}
          </Icon>
          <Text lineClamp={1}>{policy?.name}</Text>
        </Stack>
        {!isAssigned && (
          <Tooltip content="Not assigned to any group">
            <Icon color="red.500" size="sm" flexShrink="0">
              <LuCircleX />
            </Icon>
          </Tooltip>
        )}
      </Stack>
      <motion.div
        initial={!isCollapsed ? "show" : "hidden"}
        animate={controls}
        variants={{
          hidden: { opacity: 0, height: 0, pointerEvents: "none" },
          show: {
            opacity: 1,
            height: "auto",
            pointerEvents: "all",
          },
        }}
        transition={{
          duration: 0.2,
        }}
      >
        <Stack direction="column" gap="0" pl="4">
          {policy.rules?.map((rule) => (
            <RuleItem key={rule?.id} policy={policy} rule={rule} />
          ))}
        </Stack>
      </motion.div>
    </Stack>
  )
}