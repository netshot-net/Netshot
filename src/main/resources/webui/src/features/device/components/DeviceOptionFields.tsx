import { DeviceOptionDefinition, DriverOptionType } from "@/types"
import { Stack } from "@chakra-ui/react"
import { useEffect } from "react"
import { Control, UseFormSetValue, useWatch } from "react-hook-form"
import DriverValueField from "./DriverValueField"

export type DeviceOptionFormValue = string | boolean

export type DeviceOptionFieldsProps = {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  control: Control<any>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  setValue: UseFormSetValue<any>
  optionDefinitions: Record<string, DeviceOptionDefinition> | undefined
}

function defaultFormValue(definition: DeviceOptionDefinition): DeviceOptionFormValue {
  if (definition.type === DriverOptionType.Boolean) {
    return Boolean(definition.defaultValue)
  }
  return (definition.defaultValue as string) ?? ""
}

/**
 * Renders one field per option declared by the selected device driver's
 * `Options` descriptor. Backed by an `options` object on the form (keyed by
 * option name), kept in sync with the driver's option definitions - mirrors
 * how `DeviceAccessFields` reacts to the driver's `accessDefinitions`.
 */
export default function DeviceOptionFields({ control, setValue, optionDefinitions }: DeviceOptionFieldsProps) {
  const optionNamesKey = Object.keys(optionDefinitions ?? {}).sort().join(",")
  const currentOptions = useWatch({ control, name: "options" }) as Record<string, DeviceOptionFormValue> | undefined

  useEffect(() => {
    const defs = Object.values(optionDefinitions ?? {})
    const next: Record<string, DeviceOptionFormValue> = {}
    defs.forEach((def) => {
      const existing = currentOptions?.[def.name]
      next[def.name] = existing === undefined ? defaultFormValue(def) : existing
    })
    setValue("options", next, { shouldDirty: false })
    // Only re-sync when the set of options actually changes (e.g. device type
    // switched), not on every render - otherwise in-progress input gets wiped.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [optionNamesKey])

  if (!optionDefinitions || Object.keys(optionDefinitions).length === 0) {
    return null
  }

  return (
    <Stack gap="5">
      {Object.values(optionDefinitions).map((def) => (
        <DriverValueField
          key={def.name}
          control={control}
          name={`options.${def.name}`}
          definition={{
            type: def.type,
            label: def.title,
            choices: def.choices,
          }}
        />
      ))}
    </Stack>
  )
}

/** Turns the form's `options` values into the typed map the REST API expects - real booleans stay booleans, not stringified. */
export function buildOptionsPayload(
  options: Record<string, DeviceOptionFormValue> | undefined,
  optionDefinitions: Record<string, DeviceOptionDefinition> | undefined
): Record<string, DeviceOptionFormValue> {
  const result: Record<string, DeviceOptionFormValue> = {}
  Object.values(optionDefinitions ?? {}).forEach((def) => {
    const value = options?.[def.name]
    if (value === undefined) {
      return
    }
    result[def.name] = value
  })
  return result
}
