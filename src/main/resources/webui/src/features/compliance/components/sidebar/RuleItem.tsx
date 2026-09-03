import { Icon, Stack, Text } from "@chakra-ui/react"
import { useEffect, useMemo, useRef } from "react"
import { useNavigate, useParams } from "react-router"

import { Policy, LightRule, RuleType } from "@/types"

import { LuAlignLeft } from "react-icons/lu"
import { SiJavascript, SiPython } from "react-icons/si"

export type RuleItemProps = {
  policy: Policy
  rule: LightRule
}

export default function RuleItem(props: RuleItemProps) {
  const { policy, rule } = props
  const { policyId, ruleId } = useParams()
  const navigate = useNavigate()

  const iconEl = useMemo(() => {
    if (rule.type === RuleType.Javascript) {
      return (
        <SiJavascript />
      );
    } else if (rule.type === RuleType.Python) {
      return (
        <SiPython />
      );
    } else {
      return (
        <LuAlignLeft />
      );
    }
  }, [rule])

  const isActive = useMemo(
    () => +(policyId ?? 0) === policy.id && +(ruleId ?? 0) === rule.id,
    [policyId, policy, ruleId, rule]
  )

  const isDisabled = !rule?.enabled

  const ruleRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (isActive && ruleRef.current) {
      ruleRef.current.scrollIntoView({ block: "nearest" })
    }
  }, [isActive])

  return (
    <Stack
      ref={ruleRef}
      direction="row"
      justifyContent="space-between"
      alignItems="center"
      borderRadius="md"
      height="36px"
      bg={isActive ? "green.50" : "white"}
      transition="all .2s ease"
      _hover={{
        bg: isActive ? "green.50" : "grey.50",
      }}
      userSelect="none"
      cursor="pointer"
      px="3"
      onClick={() => navigate(`./config/${policy?.id}/${rule?.id}`)}
    >
      <Stack direction="row" gap="3" alignItems="center">
        <Icon color="green.600" size="md" opacity={isDisabled ? 0.5 : 1}>
          {iconEl}
        </Icon>
        <Text lineClamp={1} opacity={isDisabled ? 0.5 : 1}>{rule?.name}</Text>
      </Stack>
    </Stack>
  )
}
