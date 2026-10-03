import {
  Combobox,
  Field,
  Icon,
  Portal,
  TagsInput,
  createListCollection,
  useCombobox,
  useTagsInput,
} from "@chakra-ui/react"
import { useId, useMemo, useState } from "react"
import { FieldPath, FieldValues, useController, UseControllerProps } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuTag } from "react-icons/lu"
import { useDeviceTags } from "../api"

/** Same limit as the server side (Device.MAX_TAG_LENGTH). */
export const MAX_DEVICE_TAG_LENGTH = 255

const MAX_SUGGESTIONS = 50

export type DeviceTagsInputProps<
  TFieldValues extends FieldValues,
  TName extends FieldPath<TFieldValues>,
> = {
  label?: string
  placeholder?: string
  helperText?: string
  disabled?: boolean
} & UseControllerProps<TFieldValues, TName>

/**
 * Editable list of device tags (form field holding a `string[]`), with
 * autocompletion of the tags already existing on other devices.
 */
export default function DeviceTagsInput<
  TFieldValues extends FieldValues,
  TName extends FieldPath<TFieldValues>,
>({ label, placeholder, helperText, disabled, ...controllerProps }: DeviceTagsInputProps<TFieldValues, TName>) {
  const { t } = useTranslation()
  const baseId = useId()
  const { data: existingTags = [] } = useDeviceTags()
  const [inputValue, setInputValue] = useState("")

  const { field } = useController(controllerProps)
  const value: string[] = useMemo(() => (Array.isArray(field.value) ? field.value : []), [field.value])

  const collection = useMemo(() => {
    const query = inputValue.trim().toLowerCase()
    const items = existingTags
      .filter((tag) => !value.includes(tag) && tag.toLowerCase().includes(query))
      .slice(0, MAX_SUGGESTIONS)
    return createListCollection({ items })
  }, [existingTags, value, inputValue])

  // The combobox and the tags input share the same input/control elements
  const ids = { input: `device-tags-input-${baseId}`, control: `device-tags-control-${baseId}` }

  const tags = useTagsInput({
    ids,
    value,
    disabled,
    addOnPaste: true,
    delimiter: ",",
    maxLength: MAX_DEVICE_TAG_LENGTH,
    inputValue,
    onInputValueChange: (details) => setInputValue(details.inputValue),
    validate: (details) => details.inputValue.trim().length > 0 && !details.value.includes(details.inputValue.trim()),
    onValueChange: (details) => field.onChange(details.value.map((tag) => tag.trim())),
  })

  const combobox = useCombobox({
    ids,
    collection,
    disabled,
    value: [],
    inputValue,
    allowCustomValue: true,
    openOnClick: true,
    selectionBehavior: "clear",
    onInputValueChange: (details) => setInputValue(details.inputValue),
    onValueChange: (details) => {
      if (details.value[0]) {
        tags.addValue(details.value[0])
      }
    },
  })

  return (
    <Field.Root disabled={disabled}>
      <Combobox.RootProvider value={combobox}>
        <TagsInput.RootProvider value={tags} size="md" w="full">
          {label && <TagsInput.Label fontSize="md">{label}</TagsInput.Label>}
          <TagsInput.Control
            onBlur={field.onBlur}
            minH="10"
            borderRadius="xl"
            borderColor="grey.100"
            bg="white"
            _hover={{ borderColor: "grey.200" }}
            _focusWithin={{ borderColor: "green.400", outline: "3px solid", outlineColor: "green.600/10" }}
          >
            {value.map((tag, index) => (
              <TagsInput.Item key={tag} index={index} value={tag}>
                <TagsInput.ItemPreview>
                  <Icon size="xs" color="fg.muted">
                    <LuTag />
                  </Icon>
                  <TagsInput.ItemText>{tag}</TagsInput.ItemText>
                  <TagsInput.ItemDeleteTrigger />
                </TagsInput.ItemPreview>
              </TagsInput.Item>
            ))}
            <Combobox.Input unstyled asChild>
              <TagsInput.Input placeholder={placeholder ?? t("device.tags.addTag")} />
            </Combobox.Input>
          </TagsInput.Control>
          <Portal>
            <Combobox.Positioner>
              <Combobox.Content>
                <Combobox.Empty>
                  {inputValue.trim() ? t("device.tags.pressEnterToCreate") : t("device.tags.noOtherTag")}
                </Combobox.Empty>
                {collection.items.map((item) => (
                  <Combobox.Item key={item} item={item}>
                    <Combobox.ItemText>{item}</Combobox.ItemText>
                  </Combobox.Item>
                ))}
              </Combobox.Content>
            </Combobox.Positioner>
          </Portal>
          <TagsInput.HiddenInput name={field.name} />
        </TagsInput.RootProvider>
      </Combobox.RootProvider>
      {helperText && <Field.HelperText>{helperText}</Field.HelperText>}
    </Field.Root>
  )
}
