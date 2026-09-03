import { useArrowKeyNavigation } from "@/hooks"
import { Policy, LightRule } from "@/types"
import { Center, Spinner, Stack, Text } from "@chakra-ui/react"
import { useCallback, useMemo } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router"
import { usePoliciesWithSearch } from "../../api"
import { useComplianceSidebar } from "../../contexts/ComplianceSidebarProvider"
import PolicyItem from "./PolicyItem"

type FlatItem = { policy: Policy; rule?: LightRule }

export default function ComplianceSidebarSearchList() {
  const ctx = useComplianceSidebar()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { policyId, ruleId } = useParams()

  const { data: policies, isPending } = usePoliciesWithSearch(ctx.query)

  const items = useMemo(() => {
    const result: FlatItem[] = []
    policies?.forEach((policy) => {
      result.push({ policy })
      policy.rules?.forEach((rule) => result.push({ policy, rule }))
    })
    return result
  }, [policies])

  const activeIndex = items.findIndex(
    (item) =>
      +(policyId ?? 0) === item.policy.id && (item.rule ? +(ruleId ?? 0) === item.rule.id : !ruleId)
  )

  const onNavigate = useCallback(
    (item: FlatItem) => {
      navigate(item.rule ? `./config/${item.policy.id}/${item.rule.id}` : `./config/${item.policy.id}`)
    },
    [navigate]
  )

  useArrowKeyNavigation({
    items,
    activeIndex,
    onNavigate,
  })

  if (isPending) {
    return (
      <Stack alignItems="center" justifyContent="center" py="6" flex="1">
        <Spinner />
      </Stack>
    )
  }

  if (policies?.length === 0) {
    return (
      <Center flex="1">
        <Text>{t("policy.notFound")}</Text>
      </Center>
    )
  }

  return (
    <Stack px="6" gap="3" overflow="auto" flex="1">
      {policies?.map((policy) => (
        <PolicyItem key={policy?.id} policy={policy} forceExpand />
      ))}
    </Stack>
  )
}
