import { Option } from "@/types"
import { Button, Field, IconButton, Menu, Portal } from "@chakra-ui/react"
import { useRef, useState } from "react"
import { Control, FieldPath, FieldValues, useWatch, UseFormSetValue } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuKeyRound, LuVault } from "react-icons/lu"
import FormControl, { FormControlType } from "./FormControl"

export type VaultableInputProps<TFieldValues extends FieldValues> = {
  label: string
  /** Base field name (e.g. "password") - the form must also carry the
   * `${name}VaultInstanceId`/`${name}VaultPath` sibling fields (the latter
   * combining the KV path and the key within it, e.g. "app/creds/password"). */
  name: FieldPath<TFieldValues>
  control: Control<TFieldValues>
  setValue: UseFormSetValue<TFieldValues>
  fieldType?: FormControlType.Password | FormControlType.Text | FormControlType.LongText
  /** A value is needed, unless left unchanged (see `allowUnchanged`). */
  required?: boolean
  allowUnchanged?: boolean
  placeholder?: string
  helperText?: string
  /** Available Vault instances, as menu options (label = name, value = ID). "Local" is added automatically. */
  vaultInstances: Option<number>[]
  rows?: number
  autosize?: boolean
  mono?: boolean
}

export default function VaultableInput<TFieldValues extends FieldValues>(
  props: VaultableInputProps<TFieldValues>
) {
  const {
    label,
    name,
    control,
    setValue,
    fieldType = FormControlType.Text,
    required = false,
    allowUnchanged = false,
    placeholder,
    helperText,
    vaultInstances,
    rows,
    autosize,
    mono,
  } = props
  const { t } = useTranslation()

  const vaultInstanceIdField = `${name}VaultInstanceId` as FieldPath<TFieldValues>
  const vaultPathField = `${name}VaultPath` as FieldPath<TFieldValues>

  const localValue = useWatch({ control, name })
  const vaultInstanceId = useWatch({ control, name: vaultInstanceIdField })
  const isVaultBacked = vaultInstanceId !== null && vaultInstanceId !== undefined
  const hasVaultInstances = vaultInstances.length > 0
  const currentVaultInstance = vaultInstances.find((instance) => instance.value === vaultInstanceId)
  // Local value set aside while the field is switched to Vault, restored if switched back
  const savedLocalValueRef = useRef<{ value: unknown } | null>(null)
  // Set once a field which was Vault-backed when loaded is switched to local: there is
  // no stored local value, so it can't be left unchanged
  const [hasNoStoredLocalValue, setHasNoStoredLocalValue] = useState(false)
  const canStayUnchanged = allowUnchanged && !hasNoStoredLocalValue
  const isRequired = required && !canStayUnchanged

  function selectVaultInstance(value: number | null) {
    if (value === null) {
      if (isVaultBacked) {
        if (savedLocalValueRef.current) {
          setValue(name, savedLocalValueRef.current.value as never)
          savedLocalValueRef.current = null
        } else {
          // Already Vault-backed when loaded, no local value to keep: it needs a new one
          setValue(name, "" as never)
          setHasNoStoredLocalValue(true)
        }
      }
      setValue(vaultPathField, "" as never)
    } else {
      if (!isVaultBacked) {
        savedLocalValueRef.current = { value: localValue }
      }
      setValue(name, null as never)
    }
    setValue(vaultInstanceIdField, value as never)
  }

  const vaultPicker = hasVaultInstances ? (
    <Menu.Root
      onSelect={(details) => {
        selectVaultInstance(details.value === "local" ? null : Number(details.value))
      }}
    >
      <Menu.Trigger asChild>
        {isVaultBacked ? (
          <Button size="xs" fontSize="sm" variant="ghost" aria-label={t("vault.selectSource")}>
            <LuVault />
            {currentVaultInstance?.label}
          </Button>
        ) : (
          <IconButton size="xs" variant="ghost" aria-label={t("vault.selectSource")}>
            <LuKeyRound />
          </IconButton>
        )}
      </Menu.Trigger>
      <Portal>
        <Menu.Positioner>
          <Menu.Content>
            <Menu.Item value="local">
              <LuKeyRound />
              {t("vault.local")}
            </Menu.Item>
            {vaultInstances.map((instance) => (
              <Menu.Item key={instance.value} value={String(instance.value)}>
                <LuVault />
                {instance.label}
              </Menu.Item>
            ))}
          </Menu.Content>
        </Menu.Positioner>
      </Portal>
    </Menu.Root>
  ) : undefined

  return (
    <Field.Root required={isRequired}>
      <Field.Label>
        {label}
        {isRequired && <Field.RequiredIndicator />}
      </Field.Label>
      {isVaultBacked ? (
        <FormControl
          key="vault"
          type={FormControlType.Text}
          placeholder={t("vault.pathPlaceholder")}
          required
          control={control}
          name={vaultPathField}
          prefix={vaultPicker}
        />
      ) : (
        <FormControl
          key="local"
          type={fieldType}
          placeholder={placeholder}
          required={isRequired}
          allowUnchanged={canStayUnchanged}
          control={control}
          name={name}
          rows={rows}
          autosize={autosize}
          mono={mono}
          prefix={vaultPicker}
        />
      )}
      {helperText && !isVaultBacked && <Field.HelperText>{helperText}</Field.HelperText>}
      {isVaultBacked && <Field.HelperText>{t("vault.pathHelperText")}</Field.HelperText>}
    </Field.Root>
  )
}
