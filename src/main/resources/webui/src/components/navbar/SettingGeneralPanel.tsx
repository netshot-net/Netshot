import api, { UpdateUserPayload } from "@/api"
import { NetshotError } from "@/api/httpClient"
import { QUERIES } from "@/constants"
import { useAuth } from "@/contexts"
import { useDialogConfig } from "@/dialog"
import { useToast, useUserLevelOptions } from "@/hooks"
import { Alert, Button, Collapsible, Stack } from "@chakra-ui/react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { LuChevronRight } from "react-icons/lu"
import FormControl, { FormControlType } from "../FormControl"
import { Select } from "../Select"

type UserForm = {
  username: string
  level: string
  password: string
  newPassword: string
  confirmNewPassword: string
}

export function SettingGeneralPanel() {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const userLevelOptions = useUserLevelOptions()
  const dialogConfig = useDialogConfig()
  const [isPasswordSectionOpen, setIsPasswordSectionOpen] = useState(false)

  const defaultValues = useMemo(
    () =>
      ({
        username: user?.username,
        level: user?.level?.toString(),
        password: "",
        newPassword: "",
        confirmNewPassword: "",
      }) as UserForm,
    [user]
  )

  const isRemoteUser = useMemo(() => !user?.local, [user])

  const form = useForm<UserForm>({
    mode: "onChange",
    defaultValues,
  })

  const mutation = useMutation({
    mutationFn: async (payload: UpdateUserPayload) => api.user.update(payload),
    onSuccess() {
      toast.success({
        title: t("common.success"),
        description: t("auth.passwordSuccessfullyUpdated"),
      })

      queryClient.invalidateQueries({ queryKey: [QUERIES.USER] })
      setIsPasswordSectionOpen(false)
    },
    onError(err: NetshotError) {
      toast.error(err)
    },
  })

  const submit = form.handleSubmit(async (values: UserForm) => {
    mutation.mutate({
      username: values.username,
      password: values.password,
      newPassword: values.newPassword,
    })
  })

  const close = () => {
    dialogConfig.close()
  }

  return (
    <Stack gap="5" asChild flex="1" overflow="auto">
      <form onSubmit={submit}>
        <Stack gap="6" flex="1" overflow="auto">
          <FormControl
            label={t("user.username")}
            placeholder={t("common.eG", { example: "admin" })}
            control={form.control}
            readOnly
            name="username"
          />
          <Select
            control={form.control}
            readOnly
            name="level"
            options={userLevelOptions.options}
            label={t("common.role")}
          />
          {isRemoteUser ? (
            <Alert.Root variant="subtle" colorPalette="green">
              <Alert.Description>{t("auth.remotelyAuthenticated")}</Alert.Description>
            </Alert.Root>
          ) : (
            <Collapsible.Root
              open={isPasswordSectionOpen}
              onOpenChange={(details) => setIsPasswordSectionOpen(details.open)}
            >
              <Collapsible.Trigger
                cursor="pointer"
                paddingY="3"
                display="flex"
                gap="2"
                alignItems="center"
                fontWeight="medium"
              >
                <Collapsible.Indicator
                  transition="transform 0.2s"
                  _open={{ transform: "rotate(90deg)" }}
                >
                  <LuChevronRight />
                </Collapsible.Indicator>
                {t("auth.changePassword")}
              </Collapsible.Trigger>
              <Collapsible.Content>
                <Stack gap="6" p="4">
                  <FormControl
                    type={FormControlType.Password}
                    label={t("auth.currentPassword")}
                    placeholder={t("auth.enterYourPassword")}
                    required
                    control={form.control}
                    name="password"
                  />
                  <FormControl
                    type={FormControlType.Password}
                    label={t("auth.password")}
                    placeholder={t("auth.enterYourNewPassword")}
                    required
                    control={form.control}
                    name="newPassword"
                  />
                  <FormControl
                    type={FormControlType.Password}
                    label={t("auth.confirmPassword")}
                    placeholder={t("auth.confirmNewPassword")}
                    required
                    control={form.control}
                    name="confirmNewPassword"
                    rules={{
                      validate(value, values) {
                        return value === values.confirmNewPassword || t("auth.passwordsDontMatch")
                      },
                    }}
                  />
                  <Stack direction="row" gap="3" justifyContent="flex-end">
                    <Button
                      type="submit"
                      variant="primary"
                      loading={mutation.isPending}
                      disabled={!form.formState.isValid}
                    >
                      {t("common.applyChanges")}
                    </Button>
                  </Stack>
                </Stack>
              </Collapsible.Content>
            </Collapsible.Root>
          )}
        </Stack>
        <Stack direction="row" gap="3" justifyContent="flex-end">
          <Button type="button" onClick={close}>
            {t("common.close")}
          </Button>
        </Stack>
      </form>
    </Stack>
  )
}
