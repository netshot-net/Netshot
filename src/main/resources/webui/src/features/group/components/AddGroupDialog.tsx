import api from "@/api"
import type { CreateGroupPayload } from "@/api/group"
import { NetshotError } from "@/api/httpClient"
import { LuCode, LuServer } from "react-icons/lu"
import { QUERIES } from "@/constants"
import { useDialogConfig } from "@/dialog"
import { useToast } from "@/hooks"
import { GroupType } from "@/types"
import { Button, CloseButton, Dialog, Heading, Icon, Portal, RadioCard, Stack, Text } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import { FormProvider, useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useLocation, useNavigate } from "react-router"
import DynamicGroupForm from "./DynamicGroupForm"
import GroupInfoPanel from "./GroupInfoPanel"
import StaticGroupForm from "./StaticGroupForm"
import { FormStep, GroupForm } from "../types"

export default function AddGroupDialog() {
  const { t } = useTranslation()
  const toast = useToast()
  const dialogConfig = useDialogConfig()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const [groupType, setGroupType] = useState<GroupType | null>(null)
  const [formStep, setFormStep] = useState(FormStep.Type)

  const title = useMemo(() => {
    if (formStep === FormStep.Type) {
      return t("group.chooseType")
    }

    return t("group.create", {
      type: groupType === GroupType.Static ? t("group.type.static") : t("group.type.dynamic"),
    })
  }, [t, formStep, groupType])

  const form = useForm<GroupForm>({
    mode: "onChange",
    defaultValues: {
      name: "",
      folder: "",
      visibleInReports: true,
      staticDevices: [],
      query: "",
    },
  })

  const createMutation = useMutation({
    mutationFn: async (values: GroupForm) => {
      const payload: CreateGroupPayload = {
        folder: values.folder,
        name: values.name,
        type: groupType === GroupType.Static ? "STATIC" : "DYNAMIC",
        hiddenFromReports: !values.visibleInReports,
      }

      if (groupType === GroupType.Static) {
        payload.devices = values.staticDevices.map((d) => d.id)
      } else {
        payload.query = values.query
      }

      return api.group.create(payload)
    },
    async onSuccess(group) {
      await queryClient.invalidateQueries({ queryKey: [QUERIES.DEVICE_GROUPS] })
      navigate(
        {
          pathname: location.pathname,
          search: `?group=${group?.id}`,
        },
        { replace: true }
      )
      close()
    },
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const close = () => {
    dialogConfig.close()

    // Wait modal disappeared before re-init (blink effect)
    setTimeout(() => {
      setFormStep(FormStep.Type)
      setGroupType(null)
      form.reset()
    }, 100)
  }

  const next = () => {
    setFormStep(FormStep.Details)
  }

  const previous = () => {
    setFormStep(FormStep.Type)
  }

  const submit = (values: GroupForm) => {
    createMutation.mutate(values)
  }

  return (
    <FormProvider {...form}>
      <Dialog.Root
        preventScroll={false}
        placement="center"
        closeOnInteractOutside={false}
        closeOnEscape={false}
        size={formStep === FormStep.Type ? "2xl" : "6xl"}
        open={dialogConfig.props.isOpen}
        onOpenChange={(e) => {
          if (!e.open) {
            close()
          }
        }}
        onExitComplete={() => {
          dialogConfig.remove()
        }}
      >
        <Portal>
          <Dialog.Backdrop />
          <Dialog.Positioner>
            <Dialog.Content
              as="form"
              h={formStep === FormStep.Details ? "55vh" : "initial"}
              onSubmit={form.handleSubmit(submit)}
            >
              <Dialog.Header display="flex" justifyContent="space-between" alignItems="center">
                <Heading as="h3" fontSize="2xl" fontWeight="semibold">
                  {title}
                </Heading>

                <Stack direction="row" gap="3" alignItems="center">
                  <Text fontSize="md" color="grey.400">
                    {t("common.stepXofY", { step: formStep === FormStep.Type ? 1 : 2, total: 2 })}
                  </Text>
                  <CloseButton size="sm" variant="outline" onClick={close} />
                </Stack>
              </Dialog.Header>
              <Dialog.Body flex="1" display="flex" overflow="auto">
                {formStep === FormStep.Type && (
                  <RadioCard.Root
                    value={groupType}
                    onValueChange={({ value }) => setGroupType(value as GroupType)}
                    orientation="horizontal"
                    width="full"
                    display="flex"
                    flexDirection="row"
                    gap="5"
                    size="lg"
                  >
                    <RadioCard.Item value={GroupType.Static} flex="1">
                      <RadioCard.ItemHiddenInput />
                      <RadioCard.ItemControl>
                        <RadioCard.ItemContent>
                          <Icon size="xl" mb="2">
                            <LuServer />
                          </Icon>
                          <RadioCard.ItemText>{t("group.type.staticLabel")}</RadioCard.ItemText>
                          <RadioCard.ItemDescription>{t("group.type.createStatic")}</RadioCard.ItemDescription>
                        </RadioCard.ItemContent>
                        <RadioCard.ItemIndicator />
                      </RadioCard.ItemControl>
                    </RadioCard.Item>
                    <RadioCard.Item value={GroupType.Dynamic} flex="1">
                      <RadioCard.ItemHiddenInput />
                      <RadioCard.ItemControl>
                        <RadioCard.ItemContent>
                          <Icon size="xl" mb="2">
                            <LuCode />
                          </Icon>
                          <RadioCard.ItemText>{t("group.type.dynamicLabel")}</RadioCard.ItemText>
                          <RadioCard.ItemDescription>{t("group.type.createDynamic")}</RadioCard.ItemDescription>
                        </RadioCard.ItemContent>
                        <RadioCard.ItemIndicator />
                      </RadioCard.ItemControl>
                    </RadioCard.Item>
                  </RadioCard.Root>
                )}

                {formStep === FormStep.Details && (
                  <Stack direction="row" gap="9" flex="1">
                    <GroupInfoPanel groupType={groupType!} />
                    {groupType === GroupType.Static && <StaticGroupForm />}
                    {groupType === GroupType.Dynamic && <DynamicGroupForm />}
                  </Stack>
                )}
              </Dialog.Body>
              <Dialog.Footer justifyContent="flex-end">
                <Stack direction="row" gap="3" alignItems="center">
                  <Button onClick={close}>{t("common.cancel")}</Button>
                  <Button onClick={previous} disabled={formStep === FormStep.Type}>
                    {t("common.previous")}
                  </Button>
                  {formStep === FormStep.Type && (
                    <Button disabled={!groupType} onClick={next}>
                      {t("common.next")}
                    </Button>
                  )}
                  {formStep === FormStep.Details && (
                    <Button
                      type="submit"
                      disabled={!form.formState.isValid}
                      loading={createMutation.isPending}
                      variant="primary"
                    >
                      {t("common.create")}
                    </Button>
                  )}
                </Stack>
              </Dialog.Footer>
            </Dialog.Content>
          </Dialog.Positioner>
        </Portal>
      </Dialog.Root>
    </FormProvider>
  )
}
