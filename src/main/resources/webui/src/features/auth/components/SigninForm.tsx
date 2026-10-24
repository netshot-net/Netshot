import api from "@/api"
import FormControl, { FormControlType } from "@/components/FormControl"
import { useAuth } from "@/contexts"
import { Button, Center, Spinner, Stack, Text } from "@chakra-ui/react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useSearchParams } from "react-router"
import { useSigninMutation, useSigninWithOidcMutation } from "../api/queries"
import { useSigninForm } from "../hooks"

export function SigninForm() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const form = useSigninForm()
  const { mutation, changePass } = useSigninMutation()
  const signinWithOidcMutation = useSigninWithOidcMutation()
  const { oidcInfo } = useAuth()
  const isOidcConnection = Boolean(oidcInfo?.endpoint && oidcInfo?.clientId)
  const [isOidcForm, setIsOidcForm] = useState<boolean>(isOidcConnection)
  const state = searchParams.get("state")
  const code = searchParams.get("code")

  const submit = form.handleSubmit((values) => {
    if (isOidcForm) {
      window.location.href = api.oidc.generateUrl(oidcInfo!.endpoint!, oidcInfo!.clientId!)
      return
    }
    mutation.mutate(values)
  })

  function toggleOidcForm() {
    setIsOidcForm((prev) => !prev)
  }

  useEffect(() => {
    if (!state || !code) return
    if (state !== api.oidc.getState()) return
    if (mutation.isPending || mutation.isSuccess) return

    api.oidc.removeState()
    signinWithOidcMutation.mutate(code)
    // `signinWithOidcMutation.mutate` (stable) is the real dependency; the
    // whole `signinWithOidcMutation` object is a fresh reference on every
    // mutation state change (pending/success), so depending on it would
    // re-fire this effect and re-submit the mutation in a loop.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [state, code, mutation.isPending, mutation.isSuccess, signinWithOidcMutation.mutate])

  if (signinWithOidcMutation.isPending) {
    return (
      <Center h="100vh">
        <Stack gap="3" alignItems="center">
          <Spinner size="lg" />
          <Text>{t("auth.connectingViaSso")}</Text>
        </Stack>
      </Center>
    )
  }

  if (isOidcForm) {
    return (
      <Stack gap="2" asChild>
        <form onSubmit={submit}>
          <Button variant="primary" type="submit">
            {t("auth.signInWithSso")}
          </Button>
          <Button variant="default" onClick={toggleOidcForm}>
            {t("auth.signInWithUsername")}
          </Button>
        </form>
        {signinWithOidcMutation.isError && (
          <Text color="red.500">{t("SSO authentication error")}</Text>
        )}
      </Stack>
    )
  }

  return (
    <Stack gap="5" asChild>
      <form onSubmit={submit}>
        <FormControl
          required
          control={form.control}
          name="username"
          label="username"
          placeholder={t("auth.enterYourUsername")}
          rules={{
            required: true,
          }}
          readOnly={changePass}
          autoComplete="username"
        />
        <FormControl
          required
          type={FormControlType.Password}
          control={form.control}
          name="password"
          label="password"
          placeholder={t("auth.enterPassword")}
          rules={{
            required: true,
          }}
          readOnly={changePass}
          autoComplete="current-password"
        />
        {changePass && (
          <FormControl
            required
            type={FormControlType.Password}
            control={form.control}
            name="newPassword"
            label="New password"
            placeholder={t("auth.enterNewPassword")}
            rules={{
              required: true,
            }}
            autoFocus
            autoComplete="new-password"
          />
        )}
        <Stack gap="2">
          <Button
            type="submit"
            loading={mutation.isPending}
            disabled={!form.formState.isValid}
            variant="primary"
          >
            {changePass ? t("auth.changePasswordAndSignIn") : t("auth.signIn")}
          </Button>
          {isOidcConnection && (
            <Button onClick={toggleOidcForm} variant="default">
              {t("auth.signInWithSso")}
            </Button>
          )}
        </Stack>
      </form>
    </Stack>
  )
}
