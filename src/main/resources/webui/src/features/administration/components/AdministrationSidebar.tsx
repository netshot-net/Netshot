import { SidebarLink } from "@/components";
import { Stack } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";

export default function AdministrationSidebar() {
  const { t } = useTranslation();

  return (
    <Stack w="full" h="full" overflow="auto" gap="0">
      <Stack gap="0" py="4" px="5" flex="1">
        <SidebarLink
          to="./user"
          label={t("user.list")}
          description={t("user.managePermissions")}
        />
        <SidebarLink
          to="./device-domain"
          label={t("domain.deviceDomains")}
          description={t("admin.manageIpAddressForDevices")}
        />
        <SidebarLink
          to="./device-credential"
          label={t("credential.deviceList")}
          description={t("credential.manage")}
        />
        <SidebarLink
          to="./vault"
          label={t("vault.list")}
          description={t("vault.manage")}
        />
        <SidebarLink
          to="./driver"
          label={t("admin.drivers")}
          description={t("device.manageType")}
        />
        <SidebarLink
          to="./api-token"
          label={t("api.tokens")}
          description={t("api.connectWithExternalApp")}
        />
        <SidebarLink
          to="./webhook"
          label={t("webhook.list")}
          description={t("policy.rule.executeExternalFunction")}
        />
        <SidebarLink
          to="./clustering"
          label={t("admin.clustering.label")}
          description={t("admin.clustering.manage")}
        />
      </Stack>
    </Stack>
  );
}
