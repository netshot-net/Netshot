import api from "@/api"
import { HttpStatus, isNetshotError, NetshotErrorCode } from "@/api/httpClient"
import { EmptyResult, MonacoEditor, Protected } from "@/components"
import { Tooltip } from "@/components/ui/tooltip"
import { useLocalization } from "@/i18n"
import { LuAsterisk, LuMessageSquareDot, LuZap, LuZapOff, LuPencil, LuTrash, LuChevronDown } from "react-icons/lu"
import { DeviceNetworkClass, Level, RuleType } from "@/types"
import {
  Badge,
  Box,
  Button,
  Flex,
  Group,
  Heading,
  IconButton,
  Menu,
  Portal,
  Skeleton,
  Spacer,
  Stack,
  Tag,
  Text,
} from "@chakra-ui/react"
import { useDeviceTypeOptions } from "@/features/device/hooks"
import { useQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useParams } from "react-router"
import { DeviceBadge } from "@/components/entity"
import DisableRuleTrigger from "../components/DisableRuleTrigger"
import EditRuleTrigger from "../components/EditRuleTrigger"
import EditRuleExemptedDeviceTrigger from "../components/EditRuleExemptedDeviceTrigger"
import EnableRuleTrigger from "../components/EnableRuleTrigger"
import RuleRemoveTrigger from "../components/RemoveRuleTrigger"
import { QUERIES } from "../constants"
import { RuleProvider } from "../contexts"

export default function ConfigurationComplianceRuleScreen() {
  const { policyId, ruleId } = useParams()
  const { t } = useTranslation()
  const { formatDate } = useLocalization()
  const { getOptionByDriver } = useDeviceTypeOptions()

  const { data: rule, isPending, isError, error } = useQuery({
    queryKey: [QUERIES.RULE_DETAIL, +(policyId ?? 0), +(ruleId ?? 0)],
    queryFn: async () => api.rule.getById(+(ruleId ?? 0)),
  })

  const { data: exemptedDevices } = useQuery({
    queryKey: [QUERIES.RULE_EXEMPTED_DEVICES, +(ruleId ?? 0)],
    queryFn: () => api.rule.getAllExemptedDevices(+(ruleId ?? 0)),
    enabled: !!rule,
  })

  const [now] = useState(() => new Date())

  const contextLabel = rule?.anyBlock
    ? t("policy.rule.atLeastOneSuchBlock")
    : t("policy.rule.allSuchBlocks")

  const fieldDescription = useMemo(() => {
    if (!rule?.field) return t("common.unknownLabel")
    if (rule.deviceDriver) {
      const attr = getOptionByDriver(rule.deviceDriver)?.value.attributes.find(
        (a) => a.name === rule.field
      )
      if (attr) return attr.title
    }
    const genericLabels: Record<string, string> = {
      contact: t("common.contact"),
      location: t("common.location"),
      name: t("common.name"),
    }
    return genericLabels[rule.field] ?? rule.field
  }, [rule, getOptionByDriver, t])

  const textLabel = useMemo(() => {
    if (!rule) return t("policy.rule.mustContain")
    const { invert, regExp, matchAll, context } = rule
    const prefix = context ? "" : "field"
    const key = (k: string) => t(`policy.rule.${prefix}${prefix ? k.charAt(0).toUpperCase() + k.slice(1) : k}`)
    if (regExp && matchAll) return invert ? key("mustNotFullyMatch") : key("mustFullyMatch")
    if (regExp) return invert ? key("mustNotMatch") : key("mustMatch")
    if (matchAll) return invert ? key("mustNotBe") : key("mustBe")
    return invert ? key("mustNotContain") : key("mustContain")
  }, [rule, t])

  const isRuleNotFound =
    isError &&
    isNetshotError(error) &&
    (error.response?.status === HttpStatus.NotFound ||
      error.code === NetshotErrorCode.RuleNotFound ||
      error.code === NetshotErrorCode.PolicyNotFound)

  if (isRuleNotFound) {
    return (
      <EmptyResult title={t("policy.rule.notFound")} description={t("policy.rule.notFoundDescription")} />
    )
  }

  const exemptionsRow = exemptedDevices?.length ? (
    <Flex>
      <Box flex="0 0 auto" w="240px">
        <Text color="grey.400">{t("policy.rule.exemptedDevices")}</Text>
      </Box>
      <Stack direction="row" gap="2" flexWrap="wrap">
        {exemptedDevices.map((exemption) => {
          const device = exemption.device
          const expired = !!exemption.expirationDate && exemption.expirationDate < now
          return (
            <Tooltip key={device?.id} content={t("time.expiresOn", { date: formatDate(exemption.expirationDate!) })}>
              <DeviceBadge networkClass={device?.networkClass as DeviceNetworkClass | undefined} colorPalette={expired ? "red" : undefined}>
                <Link to={`/app/devices/${device?.id}/general`}>{device?.name ?? device?.display}</Link>
              </DeviceBadge>
            </Tooltip>
          )
        })}
      </Stack>
    </Flex>
  ) : null

  return (
    <RuleProvider rule={rule} isLoading={isPending}>
      <Stack p="9" gap="9" flex="1">
        <Flex alignItems="center">
          <Skeleton loading={isPending}>
            <Stack direction="row" gap="3" alignItems="center">
              <Heading as="h1" fontSize="4xl">
                {rule?.name}
              </Heading>
              {rule && !rule?.enabled && (
                <Tag.Root colorPalette="grey" variant="surface">{t("common.disabled")}</Tag.Root>
              )}
            </Stack>
          </Skeleton>

          <Spacer />
          <Protected minLevel={Level.ReadWrite}>
          <Skeleton loading={isPending}>
            <Menu.Root positioning={{ placement: "bottom-end" }}>
              <Group attached>
                {rule && (
                  <EditRuleTrigger key={rule?.id} policyId={+(policyId ?? 0)} rule={rule}>
                    <Button variant="primary">
                      <LuPencil />
                      {t("common.edit")}
                    </Button>
                  </EditRuleTrigger>
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
                    {rule && (
                      <>
                        <EditRuleExemptedDeviceTrigger policyId={+(policyId ?? 0)} rule={rule}>
                          <Menu.Item value="exempted-device">
                            <LuMessageSquareDot />
                            {t("policy.rule.exemptedDevices")}
                          </Menu.Item>
                        </EditRuleExemptedDeviceTrigger>
                        {rule?.enabled ? (
                          <DisableRuleTrigger policyId={+(policyId ?? 0)} rule={rule}>
                            <Menu.Item value="disable">
                              <LuZapOff />
                              {t("common.disable")}
                            </Menu.Item>
                          </DisableRuleTrigger>
                        ) : (
                          <EnableRuleTrigger policyId={+(policyId ?? 0)} rule={rule}>
                            <Menu.Item value="enable">
                              <LuZap />
                              {t("common.enable")}
                            </Menu.Item>
                          </EnableRuleTrigger>
                        )}

                        <RuleRemoveTrigger policyId={+(policyId ?? 0)} rule={rule}>
                          <Menu.Item value="remove" color="fg.error" _hover={{ bg: "bg.error", color: "fg.error" }}>
                            <LuTrash />
                            {t("common.remove")}
                          </Menu.Item>
                        </RuleRemoveTrigger>
                      </>
                    )}
                  </Menu.Content>
                </Menu.Positioner>
              </Portal>
            </Menu.Root>
          </Skeleton>
          </Protected>
        </Flex>
        {rule?.type === RuleType.Text && (
          <Stack gap="3">
            <Flex>
              <Box flex="0 0 auto" w="240px">
                <Text color="grey.400">{t("device.type")}</Text>
              </Box>
              <Skeleton loading={isPending}>
                {rule?.deviceDriver
                  ? <Text>{getOptionByDriver(rule.deviceDriver)?.value.description ?? rule.deviceDriver}</Text>
                  : <Badge size="lg" variant="outline" marginTop="-3px" marginBottom="-3px"><LuAsterisk />{t("common.any")}</Badge>
                }
              </Skeleton>
            </Flex>
            <Flex>
              <Box flex="0 0 auto" w="240px">
                <Text color="grey.400">{t("policy.rule.fieldToCheck")}</Text>
              </Box>
              <Skeleton loading={isPending}>
                <Text>{fieldDescription}</Text>
              </Skeleton>
            </Flex>
            {(rule?.context || isPending) && (
              <Flex>
                <Box flex="0 0 auto" w="240px">
                  <Text color="grey.400">{contextLabel}</Text>
                </Box>
                <Skeleton loading={isPending}>
                  <Text fontFamily="mono">{rule?.context}</Text>
                </Skeleton>
              </Flex>
            )}
            <Flex>
              <Box flex="0 0 auto" w="240px">
                <Text color="grey.400">{textLabel}</Text>
              </Box>
              <Skeleton loading={isPending}>
                <Text fontFamily="mono">{rule?.text ?? "nA"}</Text>
              </Skeleton>
            </Flex>
            {exemptionsRow}
          </Stack>
        )}

        {rule?.type === RuleType.Javascript && (
          <>
            {exemptionsRow && <Stack gap="3">{exemptionsRow}</Stack>}
            <MonacoEditor key={rule?.script} value={rule?.script} language="javascript" scriptKind="compliance" readOnly />
          </>
        )}

        {rule?.type === RuleType.Python && (
          <>
            {exemptionsRow && <Stack gap="3">{exemptionsRow}</Stack>}
            <MonacoEditor key={rule?.script} value={rule?.script} language="python" scriptKind="compliance" readOnly />
          </>
        )}
      </Stack>
    </RuleProvider>
  )
}
