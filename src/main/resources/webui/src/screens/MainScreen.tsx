import api from "@/api"
import { HttpEventType } from "@/api/httpClient"
import { client } from "@/api/client"
import { MeResult } from "@/api/user"
import { Navbar } from "@/components"
import { QUERIES, REDIRECT_SEARCH_PARAM } from "@/constants"
import { useAuth } from "@/contexts"
import { useAlertDialog, useDialogStore } from "@/dialog"
import { Stack } from "@chakra-ui/react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useCallback, useEffect, useRef } from "react"
import { useTranslation } from "react-i18next"
import { Outlet, useNavigate } from "react-router"
import withQuery from "with-query"

// Warn this many seconds before the server-side idle timeout actually kicks in
const IDLE_WARNING_MARGIN_SECONDS = 5

enum AuthState {
  AUTH_REQUIRED = 0,
  AUTHENTICATED = 1,
  REAUTH_REQUIRED = 2,
}

export function MainScreen() {
  const { t } = useTranslation()
  const dialog = useAlertDialog()
  const removeAllDialogs = useDialogStore((state) => state.removeAll)
  const { user } = useAuth()
  const navigate = useNavigate()
  // Whether there has already been a successfully authentication
  const authStateRef = useRef<AuthState>(AuthState.AUTH_REQUIRED)
  const queryClient = useQueryClient()
  const idleTimeoutRef = useRef<ReturnType<typeof setTimeout>>(undefined)

  const { data: serverInfo } = useQuery({
    queryKey: [QUERIES.SERVER_INFO],
    queryFn: api.auth.serverInfo,
  })

  const doRedirect = useCallback((hard: boolean = false) => {
    if (!location.pathname.startsWith("/signin")) {
      const url = withQuery("/signin", {
        [REDIRECT_SEARCH_PARAM]: location.pathname + location.search,
      })
      if (hard) {
        window.location.assign(url)
      } else {
        navigate(url)
      }
    }
  }, [navigate])

  useEffect(() => {
    if (user) {
      authStateRef.current = AuthState.AUTHENTICATED
    } else if (authStateRef.current !== AuthState.REAUTH_REQUIRED) {
      doRedirect()
    }
  }, [doRedirect, user])

  const requireReauth = useCallback(() => {
    if (authStateRef.current === AuthState.AUTHENTICATED) {
      removeAllDialogs()
      dialog.open({
        title: t("auth.sessionExpired"),
        description: t("auth.pleaseReAuthenticate"),
        closeButton: {
          label: t("auth.goToLogin"),
          props: {
            variant: "primary",
          },
        },
        onCancel() {
          doRedirect(true)
        },
      })
      authStateRef.current = AuthState.REAUTH_REQUIRED
    }
    queryClient.setQueryData<MeResult>([QUERIES.USER], (prev) => prev ? { ...prev, user: null } : prev)
  }, [dialog, doRedirect, queryClient, removeAllDialogs, t])

  useEffect(() => {
    // Register callback on forbidden (401/403) responses
    client.on(HttpEventType.Forbidden, requireReauth)
    return () => {
      client.off(HttpEventType.Forbidden)
    }
  }, [requireReauth])

  useEffect(() => {
    const maxIdleTimeout = serverInfo?.maxIdleTimeout
    if (!maxIdleTimeout) {
      return
    }

    const scheduleIdleWarning = () => {
      clearTimeout(idleTimeoutRef.current)
      idleTimeoutRef.current = setTimeout(
        requireReauth,
        Math.max(0, maxIdleTimeout - IDLE_WARNING_MARGIN_SECONDS) * 1000
      )
    }

    // Any request to the backend counts as activity: reschedule the warning from there
    client.on(HttpEventType.Activity, scheduleIdleWarning)
    scheduleIdleWarning()

    return () => {
      client.off(HttpEventType.Activity)
      clearTimeout(idleTimeoutRef.current)
    }
  }, [requireReauth, serverInfo?.maxIdleTimeout])

  return (
    <Stack h="100vh" gap="0">
      <Navbar />
      <Stack as="main" flex="1" overflow="auto">
        <Outlet />
      </Stack>
    </Stack>
  )
}
