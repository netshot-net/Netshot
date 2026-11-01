import api from "@/api"
import { useArrowKeyNavigation } from "@/hooks"
import type { Diagnostic } from "@/api/diagnostic"
import { Center, Spinner, Stack, Text } from "@chakra-ui/react"
import { useQuery } from "@tanstack/react-query"
import { useCallback, useEffect } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router"
import { QUERIES } from "../../constants"
import { useDiagnosticSidebar } from "../../contexts/DiagnosticSidebarProvider"
import DiagnosticItem from "./DiagnosticItem"

export default function DiagnosticSidebarSearchList() {
  const ctx = useDiagnosticSidebar()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const params = useParams<{ id: string }>()

  const { data, isPending, isSuccess } = useQuery({
    queryKey: [QUERIES.DIAGNOSTIC_SEARCH_LIST, ctx.query],
    queryFn: () => api.diagnostic.getAll({ q: ctx.query }),
  })

  useEffect(() => {
    if (isSuccess) {
      ctx.setTotal(data.length)
      ctx.setData(data)
    }
  }, [isSuccess, data, ctx])

  const activeIndex = data?.findIndex((diagnostic) => diagnostic.id === +(params?.id ?? "0")) ?? -1

  const onNavigate = useCallback(
    (diagnostic: Diagnostic) => navigate(`./${diagnostic.id}`),
    [navigate]
  )

  useArrowKeyNavigation({
    items: data ?? [],
    activeIndex,
    onNavigate,
  })

  if (isPending) {
    return (
      <Stack alignItems="center" justifyContent="center" py="6" flex="1">
        <Spinner />
      </Stack>
    )
  }

  if (data?.length === 0) {
    return (
      <Center flex="1">
        <Text>{t("diagnostic.notFound")}</Text>
      </Center>
    )
  }

  return (
    <Stack py="4" px="5" gap="0" overflow="auto" flex="1">
      {data?.map((diagnostic) => (
        <DiagnosticItem diagnostic={diagnostic} key={diagnostic?.id} />
      ))}
    </Stack>
  )
}
