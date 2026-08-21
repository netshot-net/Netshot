import api from "@/api"
import { EmptyResult, VirtualizedDataTable } from "@/components"
import { useDialogConfig } from "@/dialog"
import { DeviceBadge, DeviceGroupBadge, DeviceSoftwareLevelBadge } from "@/components/entity"
import { DeviceSoftwareLevel, GroupDeviceBySoftwareLevel } from "@/types"
import { CloseButton, Dialog, Heading, Portal, Skeleton, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { createColumnHelper } from "@tanstack/react-table"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate } from "react-router"
import { QUERIES } from "../constants"

export type SoftwareComplianceDialogProps = {
  groupId: number
  groupName: string
  level: DeviceSoftwareLevel
}

const columnHelper = createColumnHelper<GroupDeviceBySoftwareLevel>()

export default function SoftwareComplianceDialog(props: SoftwareComplianceDialogProps) {
  const { groupId, groupName, level } = props
  const { t } = useTranslation()
  const dialogConfig = useDialogConfig()
  const navigate = useNavigate()

  const { data, isPending } = useQuery({
    queryKey: [QUERIES.SOFTWARE_COMPLIANCE_DEVICES, groupId, level],
    queryFn: async () => (await api.report.getAllGroupDevicesBySoftwareLevel(groupId, level)) ?? [],
  })

  function goToDevice(device: GroupDeviceBySoftwareLevel) {
    dialogConfig.close()
    navigate(`/app/devices/${device.id}/compliance?open=software&group=${groupId}`)
  }

  const columns = useMemo(
    () => [
      columnHelper.accessor("name", {
        cell: (info) => (
          <DeviceBadge networkClass={info.row.original.networkClass}>
            <Link
              to={`/app/devices/${info.row.original.id}/compliance?open=software&group=${groupId}`}
              onClick={(evt) => {
                evt.stopPropagation()
                dialogConfig.close()
              }}
            >
              {info.getValue()}
            </Link>
          </DeviceBadge>
        ),
        header: t("device.label"),
      }),
      columnHelper.accessor("family", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("common.family"),
      }),
      columnHelper.accessor("softwareVersion", {
        cell: (info) => <Text>{info.getValue()}</Text>,
        header: t("compliance.software.version"),
      }),
    ],
    // eslint-disable-next-line @eslint-react/exhaustive-deps
    [t]
  )

  return (
    <Dialog.Root
      scrollBehavior="inside"
      size="xl"
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
          <Dialog.Content height="80vh">
            <Dialog.Header display="flex" alignItems="center" gap="4">
              <Heading as="h3" fontSize="2xl" fontWeight="semibold">
                {t("compliance.software.versionLabel")}
              </Heading>
              <DeviceGroupBadge id={groupId} name={groupName} onClick={() => dialogConfig.close()} />
              <DeviceSoftwareLevelBadge level={level} />
            </Dialog.Header>
            <Dialog.Body overflowY="auto" display="flex" flexDirection="column" flex="1" pb="7">
              {isPending ? (
                <Stack gap="3">
                  <Skeleton h="60px" />
                  <Skeleton h="60px" />
                  <Skeleton h="60px" />
                  <Skeleton h="60px" />
                </Stack>
              ) : (
                <>
                  {(data?.length ?? 0) > 0 ? (
                    <VirtualizedDataTable
                      data={data ?? []}
                      columns={columns}
                      onClickRow={goToDevice}
                      primaryKey="id"
                      flex="1"
                      minH="0"
                    />
                  ) : (
                    <EmptyResult
                      title={t("device.none")}
                      description={t("device.withLevelAppearsWhenSoftwareRuleValidated", {
                        level: t(level),
                      })}
                    />
                  )}
                </>
              )}
            </Dialog.Body>
            <Dialog.CloseTrigger asChild>
              <CloseButton size="sm" variant="outline" />
            </Dialog.CloseTrigger>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  )
}
