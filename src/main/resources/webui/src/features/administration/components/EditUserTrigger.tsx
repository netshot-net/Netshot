import api from "@/api"
import { PASSWORD_UNCHANGED } from "@/components/FormControl"
import { NetshotError } from "@/api/httpClient"
import { MUTATIONS } from "@/constants"
import { useFormDialogWithMutation } from "@/dialog"
import { useToast } from "@/hooks"
import { useAdminErrorHandler } from "../hooks"
import { User, UserRequest } from "@/api/admin"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useEffect, useMemo } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import React from "react"
import Slot from "@/components/Slot"
import { QUERIES } from "../constants"
import UserFormComponent, { UserForm } from "./UserForm"

export type EditUserTriggerProps = { user: User; children: React.ReactElement<Record<string, unknown>> } & Record<string, unknown>

export default function EditUserTrigger({ user, children, ...rest }: EditUserTriggerProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const dialog = useFormDialogWithMutation()
  const handleError = useAdminErrorHandler(QUERIES.ADMIN_USERS)

  const defaultValues = useMemo(
    () => ({
      username: user?.username,
      level: user?.level?.toString(),
      isRemote: !user.local,
      password: PASSWORD_UNCHANGED,
      confirmPassword: "",
    }),
    [user]
  )

  const form = useForm<UserForm>({
    mode: "onChange",
    defaultValues,
  })

  useEffect(() => {
    form.reset(defaultValues)
  }, [defaultValues, form])

  const mutation = useMutation({
    mutationKey: MUTATIONS.ADMIN_USER_UPDATE,
    mutationFn: async (payload: UserRequest) => api.admin.updateUser(user.id!, payload),
    onError(err: NetshotError) {
      handleError(err, form)
    },
  })

  const open = () => {
    const dialogRef = dialog.open(MUTATIONS.ADMIN_USER_UPDATE, {
      title: t("user.edit"),
      description: <UserFormComponent freezePasswords />,
      form,
      size: "lg",
      async onSubmit(values: UserForm) {
        const change: UserRequest = {
          username: values.username,
          level: +values.level,
          local: !values.isRemote,
        }
        if (values.password !== PASSWORD_UNCHANGED) {
          change.password = values.isRemote ? undefined : (values.password ?? undefined)
        }

        await mutation.mutateAsync(change)

        dialogRef.close()
        form.reset()

        toast.success({
          title: t("common.success"),
          description: t("user.successfullyUpdated", {
            username: values.username,
          }),
        })

        queryClient.invalidateQueries({ queryKey: [QUERIES.ADMIN_USERS] })
      },
      onCancel() {
        form.reset()
      },
      submitButton: {
        label: t("common.applyChanges"),
      },
    })
  }

  return <Slot onTrigger={open} {...rest}>{children}</Slot>
}
