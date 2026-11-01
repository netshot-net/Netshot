import { HttpStatus, NetshotError } from "@/api/httpClient"
import { useToast } from "@/hooks"
import { useQueryClient } from "@tanstack/react-query"
import { FieldValues, Path, UseFormReturn } from "react-hook-form"
import { useTranslation } from "react-i18next"

/**
 * The error handler of the mutations of the administration: a toast with the description of the error, the
 * field errors set on the form, and, when the object was changed meanwhile (412), a refetch of the lists.
 */
export function useAdminErrorHandler(queryKey: string) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return function handleError<T extends FieldValues>(err: NetshotError, form?: UseFormReturn<T>) {
    if (err?.response?.status === HttpStatus.PreconditionFailed) {
      toast.error({
        title: t("common.error"),
        description: t("admin.changedMeanwhile"),
      })
      queryClient.invalidateQueries({ queryKey: [queryKey] })
      return
    }
    for (const fieldError of err?.fieldErrors ?? []) {
      if (form && fieldError.field in form.getValues()) {
        form.setError(fieldError.field as Path<T>, { type: "server", message: fieldError.message })
      }
    }
    toast.error(err)
  }
}
