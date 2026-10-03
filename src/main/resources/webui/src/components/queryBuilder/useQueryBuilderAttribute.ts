import { useDomains } from "@/features/administration/api/queries"
import { useDeviceTags } from "@/features/device/api"
import { useDeviceTypeOptions } from "@/features/device/hooks"
import {
  DeviceNetworkClass,
  DeviceSoftwareLevel,
  DeviceStatus,
  DeviceType,
} from "@/types"
import { sortAlphabetical } from "@/utils"
import { useTranslation } from "react-i18next"
import { Attribute } from "./constants"
import { AttributeOption, AttributeType } from "./types"

export function useQueryBuilderAttribute() {
  const { t } = useTranslation()
  const deviceTypeOptions = useDeviceTypeOptions()
  const domainQuery = useDomains()
  const tagQuery = useDeviceTags()

  function getAllGenericOption(): AttributeOption[] {
    const domainChoices = Array.isArray(domainQuery.data)
      ? domainQuery.data.map((domain) => ({
          label: domain?.name,
          value: domain?.id,
        }))
      : []

    const options = [
      {
        label: t("common.comments"),
        value: {
          name: Attribute.Comments,
          type: AttributeType.Text,
        },
      },
      {
        label: t("common.contact"),
        value: {
          name: Attribute.Contact,
          type: AttributeType.Text,
        },
      },
      {
        label: t("time.creationDate"),
        value: {
          name: Attribute.CreationDate,
          type: AttributeType.Date,
        },
      },
      {
        label: t("domain.label"),
        value: {
          name: Attribute.Domain,
          type: AttributeType.Enum,
          choices: domainChoices,
        },
      },
      {
        label: t("common.family"),
        value: {
          name: Attribute.Family,
          type: AttributeType.Text,
        },
      },
      {
        label: t("common.id"),
        value: {
          name: Attribute.Id,
          type: AttributeType.Id,
        },
      },
      {
        label: t("device.interface.label"),
        value: {
          name: Attribute.Interface,
          type: AttributeType.Text,
        },
      },
      {
        label: t("device.interface.ip"),
        value: {
          name: Attribute.Ip,
          type: AttributeType.IpAddress,
        },
      },
      {
        label: t("device.lastChangeDate"),
        value: {
          name: Attribute.LastChangeDate,
          type: AttributeType.Date,
        },
      },
      {
        label: t("common.location"),
        value: {
          name: Attribute.Location,
          type: AttributeType.Text,
        },
      },
      {
        label: t("device.interface.mac"),
        value: {
          name: Attribute.Mac,
          type: AttributeType.MacAddress,
        },
      },
      {
        label: t("device.module.label"),
        value: {
          name: Attribute.Module,
          type: AttributeType.Text,
        },
      },
      {
        label: t("common.name"),
        value: {
          name: Attribute.Name,
          type: AttributeType.Text,
        },
      },
      {
        label: t("device.networkClass"),
        value: {
          name: Attribute.NetworkClass,
          type: AttributeType.Enum,
          choices: [
            { label: t("device.class.firewall"), value: DeviceNetworkClass.Firewall },
            { label: t("device.class.loadBalancer"), value: DeviceNetworkClass.LoadBalancer },
            { label: t("device.class.router"), value: DeviceNetworkClass.Router },
            { label: t("common.server"), value: DeviceNetworkClass.Server },
            { label: t("device.class.switch"), value: DeviceNetworkClass.Switch },
            { label: t("device.class.switchRouter"), value: DeviceNetworkClass.SwitchRouter },
            { label: t("device.class.accessPoint"), value: DeviceNetworkClass.AccessPoint },
            {
              label: t("device.class.wirelessController"),
              value: DeviceNetworkClass.WirelessController,
            },
            { label: t("device.class.consoleServer"), value: DeviceNetworkClass.ConsoleServer },
            { label: t("device.class.voiceGateway"), value: DeviceNetworkClass.VoiceGateway },
            { label: t("common.unknownLabel"), value: DeviceNetworkClass.Unknown },
          ],
        },
      },
      {
        label: t("compliance.software.level"),
        value: {
          name: Attribute.SoftwareLevel,
          type: AttributeType.Enum,
          choices: [
            { label: t("compliance.software.gold"), value: DeviceSoftwareLevel.GOLD },
            { label: t("compliance.software.silver"), value: DeviceSoftwareLevel.SILVER },
            { label: t("compliance.software.bronze"), value: DeviceSoftwareLevel.BRONZE },
            { label: t("common.unknownLabel"), value: DeviceSoftwareLevel.UNKNOWN },
          ],
        },
      },
      {
        label: t("compliance.software.version"),
        value: {
          name: Attribute.SoftwareVersion,
          type: AttributeType.Text,
        },
      },
      {
        label: t("common.status"),
        value: {
          name: Attribute.Status,
          type: AttributeType.Enum,
          choices: [
            { label: t("device.status.production"), value: DeviceStatus.Production },
            { label: t("common.disabled"), value: DeviceStatus.Disabled },
            { label: t("device.status.preproduction"), value: DeviceStatus.PreProduction },
          ],
        },
      },
      {
        label: t("device.tags.label"),
        value: {
          name: Attribute.Tag,
          type: AttributeType.Tag,
          choices: (tagQuery.data ?? []).map((tag) => ({ label: tag, value: tag })),
        },
      },
      {
        label: t("common.type"),
        value: {
          name: Attribute.Type,
          type: AttributeType.Enum,
          choices: deviceTypeOptions.getOptionsWithName()
            .map(o => ({ label: o.label, value: o.label })),
        },
      },
      {
        label: t("device.virtualName"),
        value: {
          name: Attribute.VirtualName,
          type: AttributeType.Text,
        },
      },
      {
        label: t("common.vrf"),
        value: {
          name: Attribute.Vrf,
          type: AttributeType.Text,
        },
      },
      {
        label: t("compliance.hardware.endOfSale"),
        value: {
          name: Attribute.EndOfSaleDate,
          type: AttributeType.Date,
        },
      },
      {
        label: t("compliance.hardware.endOfLife"),
        value: {
          name: Attribute.EndOfLifeDate,
          type: AttributeType.Date,
        },
      },
      {
        label: t("device.module.serialNumber"),
        value: {
          name: Attribute.SerialNumber,
          type: AttributeType.Text,
        },
      },
    ]

    return sortAlphabetical(options, "label")
  }

  function getAllTypeSpecificOption(driver: DeviceType["name"] | null) {
    const deviceType = deviceTypeOptions.getOptionByDriver(driver)

    if (!deviceType) {
      return []
    }

    const attributes = deviceType.value.attributes

    if (!Array.isArray(attributes)) {
      return []
    }

    const options = attributes.map((attr) => ({
      label: t(attr.title),
      value: {
        name: attr.name,
        type: attr.type,
      },
    }))

    return sortAlphabetical(options, "label")
  }

  const isLoading = domainQuery.isPending && deviceTypeOptions.isPending

  return {
    getAllGenericOption,
    getAllTypeSpecificOption,
    isLoading,
  }
}
