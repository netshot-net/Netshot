import api from "@/api"
import { HttpStatus, NetshotError, NetshotErrorCode } from "@/api/httpClient"
import { MeResult } from "@/api/user"
import { QUERIES, REDIRECT_SEARCH_PARAM } from "@/constants"
import { useToast } from "@/hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useSearchParams } from "react-router"

export function useSigninMutation() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { t } = useTranslation()

  const [changePass, setChangePass] = useState<boolean>(false)

  const mutation = useMutation({
    mutationFn: api.auth.signin,
    onSuccess(data) {
      queryClient.setQueryData<MeResult>([QUERIES.USER], (prev) => ({ oidcInfo: prev?.oidcInfo ?? { endpoint: null, clientId: null }, user: data }))
      navigate(searchParams.get(REDIRECT_SEARCH_PARAM) || "/app", { replace: true })
    },
    onError(err: NetshotError) {
      if (err.response?.status === HttpStatus.Unauthorized) {
        toast.error({
          title: t("auth.failed"),
          description: t("auth.incorrectCredentials"),
        })
      } else if (err.code === NetshotErrorCode.ExpiredPassword) {
        toast.error({
          title: t("auth.passwordExpired"),
          description: t("auth.needToChangePassword"),
        })
        setChangePass(true)
      } else if (
        err.response?.status === HttpStatus.BadRequest &&
        err.code === NetshotErrorCode.FailedPasswordPolicy
      ) {
        toast.error({
          title: t("auth.passwordPolicyFailed"),
          description: err.description,
        })
        setChangePass(true)
      } else {
        toast.error({
          title: t("common.serverError"),
          description: t("common.theNetshotServerDidnTReplyProperly"),
        })
      }
    },
  })

  return {
    mutation,
    changePass,
    setChangePass,
  }
}

export function useSigninWithOidcMutation() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [searchParams, setSearchParams] = useSearchParams()
  const toast = useToast()

  return useMutation({
    mutationFn(authorizationCode: string) {
      const redirectUrl = new URL(window.location.origin + window.location.pathname)

      if (searchParams.has(REDIRECT_SEARCH_PARAM)) {
        redirectUrl.searchParams.set(REDIRECT_SEARCH_PARAM, searchParams.get(REDIRECT_SEARCH_PARAM)!)
      }

      return api.auth.signinWithOidc(authorizationCode, redirectUrl.toString())
    },
    onError() {
      toast.error({
        title: t("SSO error"),
        description: t("auth.ssoError"),
      })

      setSearchParams((prev) => {
        prev.delete("code")
        prev.delete("state")

        return prev
      })
    },
    onSuccess(data) {
      queryClient.setQueryData<MeResult>([QUERIES.USER], (prev) => ({ oidcInfo: prev?.oidcInfo ?? { endpoint: null, clientId: null }, user: data }))
      navigate(searchParams.get(REDIRECT_SEARCH_PARAM) || "/app", { replace: true })
    },
  })
}
