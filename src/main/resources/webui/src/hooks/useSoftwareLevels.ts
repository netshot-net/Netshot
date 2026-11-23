import { DeviceSoftwareLevel } from "@/types"
import { createOptionHook } from "./createOptionHook"
import { tKey, type TranslationKey } from "@/i18n/tKey"

type SoftwareLevelInfo = {
  label: TranslationKey
  color: string
  isCompliant: boolean
}

const LEVELS: Record<DeviceSoftwareLevel, SoftwareLevelInfo> = {
  [DeviceSoftwareLevel.GOLD]: {
    label: tKey("compliance.software.gold"),
    color: "gold",
    isCompliant: true,
  },
  [DeviceSoftwareLevel.SILVER]: {
    label: tKey("compliance.software.silver"),
    color: "silver",
    isCompliant: true,
  },
  [DeviceSoftwareLevel.BRONZE]: {
    label: tKey("compliance.software.bronze"),
    color: "bronze",
    isCompliant: true,
  },
  [DeviceSoftwareLevel.NON_COMPLIANT]: {
    label: tKey("compliance.nonCompliant"),
    color: "red",
    isCompliant: false,
  },
  [DeviceSoftwareLevel.UNKNOWN]: {
    label: tKey("common.unknownLabel"),
    color: "black",
    isCompliant: false,
  },
}

const useLevelOptions = createOptionHook(
  Object.entries(LEVELS).filter(([, v]) => v.isCompliant).map(([key, value]) => ({
    label: value.label,
    value: key,
  })))

export function useSoftwareLevels() {
  const { options } = useLevelOptions()

  function getInfo(level: DeviceSoftwareLevel): SoftwareLevelInfo {
    return LEVELS[level]
  }

  function getColor(level: DeviceSoftwareLevel): string {
    return LEVELS[level]?.color ?? "grey"
  }

  function getDefault(): DeviceSoftwareLevel {
    return DeviceSoftwareLevel.GOLD
  }

  return {
    options,
    getInfo,
    getColor,
    getDefault,
  }
}
