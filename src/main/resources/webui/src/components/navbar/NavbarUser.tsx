import { Avatar, Button, Menu, Portal, Spacer, Stack, Text } from "@chakra-ui/react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"

import api from "@/api"
import { MeResult } from "@/api/user"
import { LanguageMenuItems } from "@/components/LanguageMenu"
import { QUERIES } from "@/constants"
import { useAuth } from "@/contexts"
import useToast from "@/hooks/useToast"
import { getUserGuideUrl } from "@/utils"

import { useUserLevelOptions } from "@/hooks"
import { Icon } from "@chakra-ui/react"
import { LuChevronDown, LuCircleHelp, LuInfo, LuLogOut, LuUser, LuBookMarked } from "react-icons/lu"
import { Link } from "react-router"
import { AboutNetshotDialog } from "./AboutNetshotDialog"
import UserSettingTrigger from "./UserSettingTrigger"

export default function NavbarUser() {
  const { user } = useAuth()
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const userLevelOptions = useUserLevelOptions()

  const { data: serverInfo } = useQuery({
    queryKey: [QUERIES.SERVER_INFO],
    queryFn: api.auth.serverInfo,
  })

  const userGuideUrl = useMemo(() => getUserGuideUrl(serverInfo?.serverVersion), [serverInfo])

  const levelLabel = useMemo(
    () => (user ? userLevelOptions.getLabelByValue(user.level) : undefined),
    [user, userLevelOptions]
  )


  const logoutMutation = useMutation({
    mutationFn: api.auth.signout,
    onError() {
      // @note: The request doesn't return JSON
      toast.error({
        title: t("common.error"),
        description: t("common.anErrorOccurred"),
      })
    },
    onSuccess() {
      toast.success({
        title: t("auth.logout"),
        description: t("auth.successfullyLoggedOut"),
      })
      queryClient.setQueryData<MeResult>([QUERIES.USER], (prev) => prev ? { ...prev, user: null } : prev)
    },
  })

  const logout = () => {
    if (!user) return
    logoutMutation.mutate(user.id)
  }

  if (!user) {
    return null
  }

  return (
    <Menu.Root
      positioning={{
        placement: "bottom-end",
      }}
    >
      <Menu.Trigger asChild>
        <Button
          variant="ghost"
          py="2"
          px="3"
          _hover={{
            bg: "green.1000",
          }}
          _active={{
            bg: "green.1000",
          }}
          _expanded={{
            bg: "green.1000",
          }}
          height="auto"
        >
          <Stack
            direction="row"
            gap="4"
            borderRadius="2xl"
            alignItems="center"
            cursor="pointer"
            transition="all .2s ease"
            userSelect="none"
          >
            <Avatar.Root shape="rounded" size="md" bg="green.500">
              <Avatar.Fallback name={user?.username} />
            </Avatar.Root>
            <Stack gap="1" alignItems="start">
              <Text fontWeight="600" color="white" lineHeight="normal">
                {user?.username}
              </Text>
              <Text fontSize="sm" color="grey.400" lineHeight="normal">
                {levelLabel}
              </Text>
            </Stack>
            <Spacer />
            <Icon color="white"><LuChevronDown /></Icon>
          </Stack>
        </Button>
      </Menu.Trigger>
      <Portal>
        <Menu.Positioner>
          <Menu.Content>
            <UserSettingTrigger>
              <Menu.Item value="account">
                <LuUser />
                {t("common.account")}
              </Menu.Item>
            </UserSettingTrigger>
            <Menu.Separator />
            <Menu.ItemGroup>
              <Menu.ItemGroupLabel>{t("common.language")}</Menu.ItemGroupLabel>
              <LanguageMenuItems />
            </Menu.ItemGroup>
            <Menu.Separator />
            <Menu.Item value="user-guide" asChild>
              <a href={userGuideUrl} target="_blank" rel="noreferrer">
                <LuCircleHelp />
                {t("admin.userGuide")}
              </a>
            </Menu.Item>
            <Menu.Item value="api-doc" asChild>
              <Link to="/app/api-browser" target="_blank" rel="noreferrer">
                <LuBookMarked />
                {t("api.documentation")}
              </Link>
            </Menu.Item>
            <AboutNetshotDialog>
              <Menu.Item value="about">
                <LuInfo />
                {t("about.label")}
              </Menu.Item>
            </AboutNetshotDialog>
            <Menu.Separator />
            <Menu.Item onSelect={logout} value="logout">
              <LuLogOut />
              {t("auth.logout")}
            </Menu.Item>
          </Menu.Content>
        </Menu.Positioner>
      </Portal>
    </Menu.Root>
  )
}
