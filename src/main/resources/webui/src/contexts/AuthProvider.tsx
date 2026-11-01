import api from "@/api"
import { HttpEventType } from "@/api/httpClient"
import { client } from "@/api/client"
import { QUERIES } from "@/constants"
import { useToast } from "@/hooks"
import { Center, Spinner, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { ReactNode, useEffect } from "react"
import { useTranslation } from "react-i18next"
import { AuthContext } from "./auth"

export type AuthProviderProps = {
  children: ReactNode
}

const GATEWAY_ERROR_TOAST_ID = "gateway-error"

export function AuthProvider({ children }: AuthProviderProps) {
  const { t } = useTranslation()
  const toast = useToast()

  const { data, isPending, isError } = useQuery({
    queryKey: [QUERIES.USER],
    queryFn: api.user.me,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })

  useEffect(() => {
    const onGatewayError = () => {
      // Fixed id: repeated 502s refresh the same toast instead of stacking new ones
      toast.error({
        id: GATEWAY_ERROR_TOAST_ID,
        title: t("common.error"),
        description: t("common.gatewayError"),
      })
    }

    client.on(HttpEventType.GatewayError, onGatewayError)
    return () => {
      client.off(HttpEventType.GatewayError)
    }
  }, [t, toast])

  if (isPending) {
    return (
      <Center h="100vh">
        <Stack gap="3" alignItems="center">
          <Spinner size="lg" />
          <Text>{t("common.netshotIsLoading")}</Text>
        </Stack>
      </Center>
    )
  }

  return (
    <AuthContext value={{ user: data?.user ?? undefined, oidcInfo: data?.oidcInfo, serverError: isError }}>
      {children}
    </AuthContext>
  )
}
