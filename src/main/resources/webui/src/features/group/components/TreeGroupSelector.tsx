import api from "@/api"
import { QUERIES } from "@/constants"
import { useFormDialog } from "@/dialog"
import { GroupBrief } from "@/types"
import { createFoldersFromGroups, findNodeWithPath, Folder } from "@/utils"
import { Badge, Box, Field, Icon, Stack, Tag, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { MouseEvent, useCallback, useMemo } from "react"
import {
  FieldPath,
  FieldValues,
  useController,
  UseControllerProps,
  useFieldArray,
  useForm,
  useFormContext,
  useWatch,
} from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuAsterisk, LuSquareStack } from "react-icons/lu"
import { useTreeOpenKeys } from "@/hooks"
import { TreeGroup } from "./TreeGroup"

type SelectGroupForm = {
  groups: Array<{ id: number }>
  isMulti: boolean
}

function SelectGroupDialog({
  items,
  initialExpandedKeys,
}: {
  items: (GroupBrief | Folder)[]
  initialExpandedKeys: string[]
}) {
  const form = useFormContext<SelectGroupForm>()
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: "groups",
    keyName: "fieldId",
  })

  const isMulti = useWatch({
    control: form.control,
    name: "isMulti",
  })

  const isSelected = useCallback(
    (group: GroupBrief) => {
      return fields.some((field) => field.id === group.id)
    },
    [fields]
  )

  const onGroupSelect = useCallback(
    (group: GroupBrief) => {
      if (!isMulti) {
        form.setValue("groups", [{ id: group.id }])
        return
      }

      if (isSelected(group)) {
        remove(fields.findIndex((field) => field.id === group.id))
      } else {
        append(group)
      }
    },
    [append, remove, fields, isSelected, isMulti, form]
  )

  const { isOpen, toggle } = useTreeOpenKeys(initialExpandedKeys)

  return (
    <Stack gap="0" py="4" px="5">
      <TreeGroup
        items={items}
        onGroupSelect={onGroupSelect}
        isSelected={isSelected}
        isFolderOpen={isOpen}
        toggleFolderOpen={toggle}
      />
    </Stack>
  )
}

export type TreeGroupSelectorProps<
  TFieldValues extends FieldValues,
  TName extends FieldPath<TFieldValues>,
> = {
  label?: string
  isMulti?: boolean
  withAny?: boolean
  required?: boolean
  readOnly?: boolean
  disabled?: boolean
} & UseControllerProps<TFieldValues, TName>

export default function TreeGroupSelector<
  TFieldValues extends FieldValues,
  TName extends FieldPath<TFieldValues>,
>(props: TreeGroupSelectorProps<TFieldValues, TName>) {
  const {
    control,
    name,
    rules,
    defaultValue,
    shouldUnregister,
    label,
    isMulti = false,
    withAny = false,
    required = false,
    readOnly = false,
    disabled = false,
  } = props

  const { t } = useTranslation()
  const dialog = useFormDialog()

  const { field, fieldState } = useController({
    control,
    name,
    rules: { required, ...rules },
    defaultValue,
    shouldUnregister,
  })

  const value = useMemo<number[]>(() => {
    if (isMulti) return (field.value as number[]) ?? []
    return field.value != null ? [field.value as number] : []
  }, [field.value, isMulti])

  const onChange = useCallback(
    (groups: number[]) => {
      field.onChange(isMulti ? groups : (groups[0] ?? null))
    },
    [field, isMulti]
  )

  const dialogForm = useForm<SelectGroupForm>({
    defaultValues: {
      groups: [],
      isMulti,
    },
  })

  const { data: groups, isLoading } = useQuery({
    queryKey: [QUERIES.DEVICE_GROUPS],
    queryFn: async () => api.group.getAll(),
  })

  const items = useMemo(() => createFoldersFromGroups(groups ?? undefined), [groups])

  const onRemove = useCallback(
    (evt: MouseEvent<HTMLButtonElement>, index: number) => {
      evt?.stopPropagation()
      onChange(value.filter((_, i) => i !== index))
    },
    [onChange, value]
  )

  const selectedGroups = useMemo(() => {
    if (!groups?.length || value.length === 0) {
      return []
    }
    return value
      .map((id) => groups.find((group) => group.id === id)?.name)
      .filter(Boolean) as string[]
  }, [groups, value])

  const open = () => {
    dialogForm.reset({
      groups: value.map((v) => ({ id: v })),
      isMulti,
    })

    const expandedKeys: string[] = []
    for (const id of value) {
      const result = findNodeWithPath(items, id)
      if (result) {
        result.path.forEach((f) => {
          if (!expandedKeys.includes(f.path)) expandedKeys.push(f.path)
        })
      }
    }

    const dialogRef = dialog.open({
      title: isMulti ? t("common.selectGroups") : t("common.selectGroup"),
      description: <SelectGroupDialog items={items} initialExpandedKeys={expandedKeys} />,
      form: dialogForm,
      onSubmit() {
        const values = dialogForm.getValues()

        onChange(values.groups.map((g) => g.id))
        field.onBlur()

        dialogRef.close()
      },
      size: "lg",
      submitButton: {
        label: t("common.select"),
      },
    })
  }

  const isDisabled = disabled || isLoading

  const tags = selectedGroups.map((group, index) => (
    <Tag.Root key={group} size="lg">
      <Icon size="xs"><LuSquareStack /></Icon>
      <Tag.Label>{group}</Tag.Label>
      <Tag.EndElement>
        <Tag.CloseTrigger type="button" onClick={(evt) => onRemove(evt, index)} />
      </Tag.EndElement>
    </Tag.Root>
  ))

  return (
    <Stack gap="1">
      <Field.Root required={required} readOnly={readOnly} disabled={isDisabled}>
        <Field.Label>
          {label ? label : isMulti ? t("common.targetGroups") : t("common.targetGroup")}
          <Field.RequiredIndicator />
        </Field.Label>
        <Box
          onClick={open}
          cursor="pointer"
          w="100%"
          border="1px solid"
          borderColor="grey.100"
          borderRadius="xl"
          minHeight="42px"
          h="auto"
          display="flex"
          flexWrap="wrap"
          transition="all .2s ease"
          py="3"
          px="3"
          gap="2"
          _hover={{
            borderColor: "grey.200",
          }}
        >
          {withAny ? (
            <>
              {value?.length > 0 ? (
                tags
              ) : (
                <Badge size="lg" variant="outline">
                  <LuAsterisk />
                  {t("common.any")}
                </Badge>
              )}
            </>
          ) : (
            <>
              {value?.length > 0 ? tags : <Text color="grey.400">{isMulti ? t("common.selectGroups") : t("common.selectGroup")}</Text>}
            </>
          )}
        </Box>
        {fieldState.error && <Field.ErrorText>{fieldState.error.message}</Field.ErrorText>}
      </Field.Root>
    </Stack>
  )
}
