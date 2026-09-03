import {
  Box,
  Button,
  Flex,
  Group,
  Heading,
  Icon,
  IconButton,
  Menu,
  Portal,
  Skeleton,
  Spacer,
  Stack,
  Tag,
  Text,
} from "@chakra-ui/react"
import { LuAlignLeft, LuChevronDown, LuPencil, LuPlus, LuTrash } from "react-icons/lu"
import { SiJavascript, SiPython } from "react-icons/si"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router"
import { Level, RuleType } from "@/types"
import AddRuleTrigger from "../components/AddRuleTrigger"
import EditPolicyTrigger from "../components/EditPolicyTrigger"
import RemovePolicyTrigger from "../components/RemovePolicyTrigger"
import { usePolicy } from "../api"
import { EmptyResult, Protected } from "@/components"
import { DeviceGroupBadge } from "@/components/entity"

function getRuleIcon(type: RuleType) {
  if (type === RuleType.Javascript) return <SiJavascript />
  if (type === RuleType.Python) return <SiPython />
  return <LuAlignLeft />
}

export default function ConfigurationCompliancePolicyScreen() {
  const { policyId } = useParams()
  const { t } = useTranslation()
  const navigate = useNavigate()

  const numericPolicyId = +(policyId ?? 0)
  const { data: policy, isPending } = usePolicy(numericPolicyId)
  const rules = policy?.rules

  if (!isPending && !policy) {
    return (
      <EmptyResult title={t("policy.notFound")} description={t("policy.notFoundDescription")} />
    )
  }

  return (
    <Stack p="9" gap="9" flex="1">
      <Flex alignItems="center">
        <Skeleton loading={isPending}>
          <Heading as="h1" fontSize="4xl">
            {policy?.name}
          </Heading>
        </Skeleton>

        <Spacer />
        <Protected minLevel={Level.ReadWrite}>
        <Skeleton loading={isPending}>
          <Menu.Root positioning={{ placement: "bottom-end" }}>
            <Group attached>
              {policy && (
                <EditPolicyTrigger policy={policy}>
                  <Button variant="primary">
                    <LuPencil />
                    {t("common.edit")}
                  </Button>
                </EditPolicyTrigger>
              )}
              <Menu.Trigger asChild>
                <IconButton variant="primary">
                  <LuChevronDown />
                </IconButton>
              </Menu.Trigger>
            </Group>

            <Portal>
              <Menu.Positioner>
                <Menu.Content>
                  {policy && (
                    <>
                      <AddRuleTrigger policy={policy}>
                        <Menu.Item value="add-rule">
                          <LuPlus />
                          {t("policy.rule.add")}
                        </Menu.Item>
                      </AddRuleTrigger>
                      <RemovePolicyTrigger policy={policy}>
                        <Menu.Item
                          value="remove"
                          color="fg.error"
                          _hover={{ bg: "bg.error", color: "fg.error" }}
                        >
                          <LuTrash />
                          {t("common.remove")}
                        </Menu.Item>
                      </RemovePolicyTrigger>
                    </>
                  )}
                </Menu.Content>
              </Menu.Positioner>
            </Portal>
          </Menu.Root>
        </Skeleton>
        </Protected>
      </Flex>

      <Stack gap="3">
        <Flex>
          <Box flex="0 0 auto" w="200px">
            <Text color="grey.400">{t("common.targetGroups")}</Text>
          </Box>
          <Skeleton loading={isPending}>
            <Stack direction="row" gap="2" flexWrap="wrap">
              {policy?.targetGroups?.length ? (
                policy.targetGroups.map((group) => (
                  <DeviceGroupBadge key={group.id} id={group.id} name={group.name} />
                ))
              ) : (
                <Text color="grey.400">—</Text>
              )}
            </Stack>
          </Skeleton>
        </Flex>
      </Stack>

      <Stack gap="4" maxW="2xl">
        <Heading as="h2" fontSize="2xl" fontWeight="semibold">
          {t("policy.rule.labelPlural")}
        </Heading>
        {!isPending && rules?.length === 0 && (
          <Text color="grey.400">{t("common.noRuleFound")}</Text>
        )}
        {rules?.map((rule) => (
          <Flex
            key={rule.id}
            alignItems="center"
            gap="3"
            p="3"
            borderRadius="md"
            borderWidth="1px"
            cursor="pointer"
            _hover={{ bg: "grey.50" }}
            onClick={() => navigate(`./${rule.id}`)}
          >
            <Icon color="green.600" size="md" opacity={rule.enabled ? 1 : 0.5}>
              {getRuleIcon(rule.type)}
            </Icon>
            <Text opacity={rule.enabled ? 1 : 0.5} flex="1" lineClamp={1}>
              {rule.name}
            </Text>
            {!rule.enabled && (
              <Tag.Root colorPalette="grey" variant="surface" size="sm">
                <Tag.Label>{t("common.disabled")}</Tag.Label>
              </Tag.Root>
            )}
          </Flex>
        ))}
        {policy && (
          <Protected minLevel={Level.ReadWrite}>
            <AddRuleTrigger policy={policy}>
              <Button variant="outline" alignSelf="flex-start">
                <LuPlus />
                {t("policy.rule.add")}
              </Button>
            </AddRuleTrigger>
          </Protected>
        )}
      </Stack>
    </Stack>
  )
}
