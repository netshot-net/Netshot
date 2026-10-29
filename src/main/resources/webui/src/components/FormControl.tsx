import { Tooltip } from "@/components/ui/tooltip"
import {
  Combobox,
  DatePicker,
  type DatePickerValueChangeDetails,
  Field,
  IconButton,
  Input,
  InputGroup,
  type InputAddonProps,
  type InputProps,
  NumberInput,
  Portal,
  SystemStyleObject,
  Textarea,
  useListCollection,
} from "@chakra-ui/react"
import { type DateValue } from "@internationalized/date"
import { ReactNode, RefObject, useCallback, useEffect, useMemo, useRef, useState } from "react"
import { FieldValues, useController, UseControllerProps } from "react-hook-form"
import { useLocalization } from "@/i18n"
import { useTranslation } from "react-i18next"

import { LuCalendar, LuEye, LuEyeOff, LuLock, LuLockOpen, LuX } from "react-icons/lu"

export const PASSWORD_UNCHANGED = null

const MAX_SUGGESTIONS = 20

export enum FormControlType {
  Text = "text",
  Password = "password",
  LongText = "longtext",
  File = "file",
  Number = "number",
  Date = "date",
  DateTime = "datetime",
  Time = "time",
  Url = "url",
}

export type FormControlProps<T extends FieldValues> = {
  label?: string
  type?: FormControlType
  helperText?: string
  uppercase?: boolean
  rows?: number
  onFocus?(): void
  suffix?: ReactNode
  prefix?: ReactNode
  allowUnchanged?: boolean
  clearable?: boolean
  autosize?: boolean
  suggestions?: string[]
  mono?: boolean
  /** Rendered attached to the end of the input as a bordered addon (e.g. a unit selector), instead of floating inside it like `suffix`. */
  endAddon?: ReactNode
  endAddonProps?: InputAddonProps
  ref?: RefObject<HTMLInputElement | HTMLTextAreaElement | null>
} & Omit<InputProps, "recipe" | "prefix"> &
  Omit<SystemStyleObject, "recipe" | "prefix"> &
  UseControllerProps<T>

function FormControl<T extends FieldValues>(props: FormControlProps<T>) {
  const {
    ref,
    label,
    placeholder,
    control,
    name,
    defaultValue,
    rules = {},
    required,
    disabled,
    type = FormControlType.Text,
    helperText,
    uppercase = false,
    rows = 10,
    onFocus,
    autoFocus,
    suffix,
    prefix,
    endAddon,
    endAddonProps,
    autoComplete,
    variant,
    allowUnchanged = false,
    clearable = false,
    autosize = false,
    suggestions,
    mono,
    min,
    max,
    step,
    size,
    ...other
  } = props

  const { t, i18n } = useTranslation()
  const { datePlaceholder, numberToCalendarDate, calendarDateToTimestamp } = useLocalization()
  const [showPassword, setShowPassword] = useState(false)
  const passwordInputRef = useRef<HTMLInputElement | HTMLTextAreaElement>(null)
  const allSuggestionsRef = useRef<string[]>([])
  const { collection: suggestionCollection, set: setSuggestionItems } = useListCollection({
    initialItems: [] as { label: string; value: string }[],
    itemToValue: (item) => item.value,
    itemToString: (item) => item.label,
  })

  useEffect(() => {
    allSuggestionsRef.current = suggestions ?? []
    setSuggestionItems(
      (suggestions ?? []).slice(0, MAX_SUGGESTIONS).map((s) => ({ label: s, value: s }))
    )
  }, [suggestions, setSuggestionItems])

  const {
    field,
    fieldState: { error },
  } = useController({
    control,
    name,
    rules: {
      required,
      ...rules,
    },
    defaultValue,
  })

  // Locked as unchanged only if there is no value yet (it can mount with one, e.g. a
  // Vault-backed field switched back to local)
  const [isUnchanged, setIsUnchanged] = useState(allowUnchanged && field.value == PASSWORD_UNCHANGED)
  const [prevFieldValue, setPrevFieldValue] = useState(field.value)

  // Re-derive `isUnchanged` from `field.value` whenever it changes (e.g. form
  // reset), resetting `showPassword` on the transition, without the extra
  // render an effect-based sync would cost.
  if (allowUnchanged && field.value !== prevFieldValue) {
    setPrevFieldValue(field.value)
    if (isUnchanged && field.value != PASSWORD_UNCHANGED) {
      setIsUnchanged(false)
      setShowPassword(false)
    } else if (!isUnchanged && field.value == PASSWORD_UNCHANGED) {
      setIsUnchanged(true)
      setShowPassword(false)
    }
  }

  useEffect(() => {
    if (!isUnchanged && allowUnchanged) {
      passwordInputRef.current?.focus()
    }
  }, [isUnchanged, allowUnchanged])

  const inputProps = {
    onChange(e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) {
      field.onChange(uppercase ? e.target.value.toUpperCase() : e)
    },
    onBlur() {
      field.onBlur()
    },
    onFocus() {
      if (onFocus) onFocus()
    },
    placeholder,
    name,
    ref(inputRef) {
      field.ref(inputRef)

      if (ref) {
        ref.current = inputRef
      }
    },
    autoFocus,
    variant,
  }

  const togglePassword = useCallback(() => {
    if (isUnchanged) {
      setIsUnchanged(false)
      field.onChange("")
    } else {
      setShowPassword((prev) => !prev)
    }
  }, [isUnchanged, field])

  const lockPassword = useCallback(() => {
    setIsUnchanged(true)
    setShowPassword(false)
    field.onChange(PASSWORD_UNCHANGED)
  }, [field])

  const dateValue = useMemo<DateValue | null>(
    () => numberToCalendarDate(field.value as number | undefined),
    [field.value, numberToCalendarDate]
  )

  const handleDateChange = useCallback(
    ({ value }: DatePickerValueChangeDetails) => {
      field.onChange(value.length === 0 ? null : calendarDateToTimestamp(value[0]))
      field.onBlur()
    },
    [field, calendarDateToTimestamp]
  )

  return (
    <Field.Root required={required} disabled={disabled} {...other}>
      {label && (
        <Field.Label>
          {label}
          <Field.RequiredIndicator />
        </Field.Label>
      )}
      {[
        FormControlType.Text,
        FormControlType.DateTime,
        FormControlType.Time,
        FormControlType.Url,
      ].includes(type) && (
        suggestions !== undefined ? (
          <Combobox.Root
            collection={suggestionCollection}
            inputValue={String(field.value ?? "")}
            onInputValueChange={({ inputValue }) => {
              const val = uppercase ? inputValue.toUpperCase() : inputValue
              field.onChange(val)
              const lower = inputValue.toLowerCase()
              setSuggestionItems(
                allSuggestionsRef.current
                  .filter((s) => s.toLowerCase().includes(lower))
                  .slice(0, MAX_SUGGESTIONS)
                  .map((s) => ({ label: s, value: s }))
              )
            }}
            onValueChange={({ value }) => {
              if (value[0] !== undefined) field.onChange(value[0])
            }}
            onInteractOutside={field.onBlur}
            onFocusOutside={field.onBlur}
            onFocus={() => { if (onFocus) onFocus() }}
            allowCustomValue
            openOnChange
            disabled={disabled}
          >
            <Combobox.Control>
              <Combobox.Input
                type={type}
                placeholder={placeholder}
                name={name}
                autoComplete="off"
                autoFocus={autoFocus}
                ref={(inputRef) => {
                  field.ref(inputRef)
                  if (ref) ref.current = inputRef
                }}
              />
              {suffix && (
                <Combobox.IndicatorGroup>
                  {suffix}
                </Combobox.IndicatorGroup>
              )}
            </Combobox.Control>
            <Portal>
              <Combobox.Positioner>
                <Combobox.Content>
                  {suggestionCollection.items.map((item) => (
                    <Combobox.Item key={item.value} item={item}>
                      <Combobox.ItemText>{item.label}</Combobox.ItemText>
                    </Combobox.Item>
                  ))}
                </Combobox.Content>
              </Combobox.Positioner>
            </Portal>
          </Combobox.Root>
        ) : (
          <InputGroup
            startAddon={prefix}
            startAddonProps={{ p: "0" }}
            endElement={suffix}
            endAddon={endAddon}
            endAddonProps={endAddonProps}
          >
            <Input
              type={type}
              value={field.value == null ? "" : String(field.value as string)}
              autoComplete={autoComplete}
              disabled={disabled}
              {...inputProps}
            />
          </InputGroup>
        )
      )}
      {type === FormControlType.Number && (
        // `suffix` overlaps the built-in steppers when floated inside the input like other
        // types, so for Number it's rendered as an addon (a separate box) instead - same as
        // `endAddon`, which takes priority if both are given.
        <InputGroup
          startElement={prefix}
          endAddon={endAddon ?? suffix}
          endAddonProps={endAddonProps}
        >
          <NumberInput.Root
            w="full"
            size={size as "xs" | "sm" | "md" | "lg"}
            variant={variant}
            name={name}
            disabled={disabled}
            value={field.value == null ? "" : String(field.value)}
            min={min !== undefined ? Number(min) : undefined}
            max={max !== undefined ? Number(max) : undefined}
            step={step !== undefined ? Number(step) : undefined}
            onValueChange={({ value }) => field.onChange(value)}
            onFocusChange={({ focused }) => {
              if (focused) {
                if (onFocus) onFocus()
              } else {
                field.onBlur()
              }
            }}
          >
            <NumberInput.Input
              placeholder={placeholder}
              autoFocus={autoFocus}
              ref={(inputRef) => {
                field.ref(inputRef)
                if (ref) ref.current = inputRef
              }}
            />
            <NumberInput.Control>
              <NumberInput.IncrementTrigger />
              <NumberInput.DecrementTrigger />
            </NumberInput.Control>
          </NumberInput.Root>
        </InputGroup>
      )}
      {type === FormControlType.Date && (
        <DatePicker.Root
          value={dateValue ? [dateValue] : []}
          onValueChange={handleDateChange}
          locale={i18n.language}
          disabled={disabled}
          closeOnSelect
          placeholder={datePlaceholder}
        >
          <DatePicker.Control>
            <DatePicker.Input />
            <DatePicker.IndicatorGroup>
              {(clearable && dateValue != null) ? (
                <DatePicker.ClearTrigger asChild>
                  <IconButton size="xs" variant="ghost" borderRadius="xl" aria-label={t("common.clear")}>
                    <LuX />
                  </IconButton>
                </DatePicker.ClearTrigger>
              ) : (
                <DatePicker.Trigger asChild>
                  <IconButton size="xs" variant="ghost" borderRadius="xl" aria-label={t("common.openCalendar")}>
                    <LuCalendar />
                  </IconButton>
                </DatePicker.Trigger>
              )}
            </DatePicker.IndicatorGroup>
          </DatePicker.Control>
          <Portal>
            <DatePicker.Positioner>
              <DatePicker.Content>
                <DatePicker.View view="day">
                  <DatePicker.Header />
                  <DatePicker.DayTable />
                </DatePicker.View>
                <DatePicker.View view="month">
                  <DatePicker.Header />
                  <DatePicker.MonthTable />
                </DatePicker.View>
                <DatePicker.View view="year">
                  <DatePicker.Header />
                  <DatePicker.YearTable />
                </DatePicker.View>
              </DatePicker.Content>
            </DatePicker.Positioner>
          </Portal>
        </DatePicker.Root>
      )}
      {type === FormControlType.Password && (
        <InputGroup
          startAddon={prefix}
          startAddonProps={{ p: "0" }}
          endElement={
            <>
              {allowUnchanged && !isUnchanged && (
                <Tooltip content={t("auth.passwordUnchanged")} positioning={{ placement: "top" }}>
                  <span>
                    <IconButton
                      size="xs"
                      variant="ghost"
                      aria-label={t("auth.passwordUnchanged")}
                      onClick={lockPassword}
                    >
                      <LuLockOpen />
                    </IconButton>
                  </span>
                </Tooltip>
              )}
              <Tooltip
                content={isUnchanged ? t("common.clickToSetNewSecret") : showPassword ? t("common.hidePassword") : t("common.showPassword")}
                positioning={{ placement: "top" }}
              >
                <span>
                  <IconButton
                    size="xs"
                    variant="ghost"
                    aria-label={isUnchanged ? t("common.clickToSetNewSecret") : showPassword ? t("common.hidePassword") : t("common.showPassword")}
                    onClick={togglePassword}
                  >
                    {isUnchanged ? <LuLock /> : showPassword ? <LuEye /> : <LuEyeOff />}
                  </IconButton>
                </span>
              </Tooltip>
              {suffix}
            </>
          }
        >
          <Input
            type={showPassword ? "text" : "password"}
            value={isUnchanged ? "••••••••" : field.value == null ? "" : String(field.value as string)}
            disabled={disabled || isUnchanged}
            autoComplete={autoComplete}
            {...inputProps}
            ref={(inputRef) => {
              inputProps.ref(inputRef)
              passwordInputRef.current = inputRef
            }}
          />
        </InputGroup>
      )}
      {type === FormControlType.LongText && (
        <InputGroup
          startAddon={prefix}
          startAddonProps={{ p: "0" }}
          endElementProps={{ alignItems: "flex-start", pt: "2" }}
          endElement={
            <>
              {allowUnchanged && !isUnchanged && (
                <Tooltip content={t("auth.passwordUnchanged")} positioning={{ placement: "top" }}>
                  <span>
                    <IconButton
                      size="xs"
                      variant="ghost"
                      aria-label={t("auth.passwordUnchanged")}
                      onClick={lockPassword}
                    >
                      <LuLockOpen />
                    </IconButton>
                  </span>
                </Tooltip>
              )}
              {allowUnchanged && isUnchanged && (
                <Tooltip content={t("common.clickToSetNewSecret")} positioning={{ placement: "top" }}>
                  <span>
                    <IconButton
                      size="xs"
                      variant="ghost"
                      aria-label={t("common.clickToSetNewSecret")}
                      onClick={togglePassword}
                    >
                      <LuLock />
                    </IconButton>
                  </span>
                </Tooltip>
              )}
              {clearable && field.value && !isUnchanged && (
                <IconButton
                  size="xs"
                  variant="ghost"
                  aria-label={t("common.clear")}
                  onClick={() => field.onChange("")}
                >
                  <LuX />
                </IconButton>
              )}
              {suffix}
            </>
          }
        >
          <Textarea
            rows={rows}
            autoresize={autosize}
            value={isUnchanged ? "••••••••" : field.value == null ? "" : String(field.value as string)}
            disabled={disabled || isUnchanged}
            fontFamily={mono ? "mono" : undefined}
            {...inputProps}
            ref={(inputRef) => {
              inputProps.ref(inputRef)
              passwordInputRef.current = inputRef
            }}
          />
        </InputGroup>
      )}
      {helperText && <Field.HelperText>{helperText}</Field.HelperText>}
      {error && <Field.HelperText color="red.500">{error?.message}</Field.HelperText>}
    </Field.Root>
  )
}

export default FormControl
