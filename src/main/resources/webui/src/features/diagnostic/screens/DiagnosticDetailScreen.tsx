import api from "@/api"
import { HttpStatus, isNetshotError } from "@/api/httpClient"
import { EmptyResult, Protected } from "@/components"
import { LuChevronDown, LuZap, LuZapOff, LuPencil, LuTrash } from "react-icons/lu"
import { DeviceGroupBadge } from "@/components/entity"
import { DiagnosticType, Level } from "@/types"
import {
  Box,
  Button,
  Flex,
  Group,
  Heading,
  IconButton,
  Menu,
  Portal,
  Separator,
  Skeleton,
  Spacer,
  Stack,
  Tag,
  Text,
} from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router"
import DisableDiagnosticTrigger from "../components/DisableDiagnosticTrigger"
import EditDiagnosticTrigger from "../components/EditDiagnosticTrigger"
import EnableDiagnosticTrigger from "../components/EnableDiagnosticTrigger"
import RemoveDiagnosticTrigger from "../components/RemoveDiagnosticTrigger"
import ScriptDiagnosticDetail from "../components/ScriptDiagnosticDetail"
import TextDiagnosticDetail from "../components/TextDiagnosticDetail"
import { QUERIES } from "../constants"
import { DiagnosticProvider } from "../contexts"

export default function DeviceDetailScreen() {
  const { id } = useParams()
  const { t } = useTranslation()

  const { data: diagnostic, isPending, isError, error } = useQuery({
    queryKey: [QUERIES.DIAGNOSTIC_DETAIL, +(id ?? "0")],
    queryFn: async () => api.diagnostic.getById(+(id ?? "0")),
  })

  const isDiagnosticNotFound =
    isError && isNetshotError(error) && error.response?.status === HttpStatus.NotFound

  if (isDiagnosticNotFound) {
    return (
      <EmptyResult title={t("diagnostic.notFound")} description={t("diagnostic.notFoundDescription")} />
    )
  }

  return (
    <DiagnosticProvider diagnostic={diagnostic} isLoading={isPending} key={id}>
      <Stack p="9" gap="6" flex="1">
        <Flex alignItems="center">
          <Skeleton loading={isPending}>
            <Stack direction="row" gap="3" alignItems="center">
              <Heading as="h1" fontSize="4xl">
                {diagnostic?.name ?? t("common.networkDeviceTitle")}
              </Heading>
              {diagnostic && !diagnostic?.enabled && (
                <Tag.Root colorPalette="grey" variant="surface">{t("common.disabled")}</Tag.Root>
              )}
            </Stack>
          </Skeleton>

          <Spacer />

          <Protected minLevel={Level.ExecureReadWrite}>
            <Skeleton loading={isPending}>
              <Menu.Root positioning={{ placement: "bottom-end" }}>
                <Group attached>
                  <EditDiagnosticTrigger diagnostic={diagnostic!}>
                    <Button variant="primary">
                      <LuPencil />
                      {t("common.edit")}
                    </Button>
                  </EditDiagnosticTrigger>
                  <Menu.Trigger asChild>
                    <IconButton variant="primary">
                      <LuChevronDown />
                    </IconButton>
                  </Menu.Trigger>
                </Group>

                <Portal>
                  <Menu.Positioner>
                    <Menu.Content>
                      {diagnostic && (
                        <>
                          {diagnostic.enabled ? (
                            <DisableDiagnosticTrigger diagnostic={diagnostic}>
                              <Menu.Item value="disable">
                                <LuZapOff />
                                {t("common.disable")}
                              </Menu.Item>
                            </DisableDiagnosticTrigger>
                          ) : (
                            <EnableDiagnosticTrigger diagnostic={diagnostic}>
                              <Menu.Item value="enable">
                                <LuZap />
                                {t("common.enable")}
                              </Menu.Item>
                            </EnableDiagnosticTrigger>
                          )}

                          <RemoveDiagnosticTrigger diagnostic={diagnostic}>
                            <Menu.Item value="remove" color="fg.error" _hover={{ bg: "bg.error", color: "fg.error" }}>
                              <LuTrash />
                              {t("common.remove")}
                            </Menu.Item>
                          </RemoveDiagnosticTrigger>
                        </>
                      )}
                    </Menu.Content>
                  </Menu.Positioner>
                </Portal>
              </Menu.Root>
            </Skeleton>
          </Protected>
        </Flex>
        <Separator />
        <Stack gap="3">
          <Flex>
            <Box flex="0 0 auto" w="240px">
              <Text color="grey.400">{t("common.targetGroup")}</Text>
            </Box>
            <Skeleton loading={isPending}>
              {diagnostic?.targetGroup ? (
                <DeviceGroupBadge id={diagnostic.targetGroup.id} name={diagnostic.targetGroup.name ?? ""} />
              ) : (
                <Text color="grey.400">—</Text>
              )}
            </Skeleton>
          </Flex>
        </Stack>
        <Separator />
        {diagnostic?.type === DiagnosticType.Simple && <TextDiagnosticDetail />}
        {(diagnostic?.type === DiagnosticType.Javascript ||
          diagnostic?.type === DiagnosticType.Python) && (
          <ScriptDiagnosticDetail />
        )}
      </Stack>
    </DiagnosticProvider>
  )
}
