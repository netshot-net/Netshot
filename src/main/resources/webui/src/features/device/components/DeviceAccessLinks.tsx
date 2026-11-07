import { Button, Stack } from "@chakra-ui/react"
import { useTranslation } from "react-i18next"

import { Tooltip } from "@/components/ui/tooltip"
import { DeviceAccess, DeviceAccessDefinition, DeviceTypeProtocol } from "@/types"

/**
 * URL scheme and standard (implicit) port for each protocol that has a natural link to
 * it - SNMP variants are omitted on purpose. The standard port is the well-known port
 * for the scheme itself (e.g. 22 for ssh://), not the driver's configured default port,
 * and is left off the URL when the effective port matches it.
 */
const PROTOCOL_LINK: Partial<Record<DeviceTypeProtocol, { scheme: string; standardPort: number }>> = {
  [DeviceTypeProtocol.Ssh]: { scheme: "ssh", standardPort: 22 },
  [DeviceTypeProtocol.Telnet]: { scheme: "telnet", standardPort: 23 },
  [DeviceTypeProtocol.Http]: { scheme: "http", standardPort: 80 },
  [DeviceTypeProtocol.Https]: { scheme: "https", standardPort: 443 },
}

export type DeviceAccessLinksProps = {
  mgmtAddress: string
  accesses: DeviceAccess[]
  accessDefinitions: DeviceAccessDefinition[] | undefined
}

type ResolvedAccessLink = {
  accessName: string
  scheme: string
  url: string
  priority: number
}

/** Resolves the effective host:port for one active access, honoring its address/port override, then the driver's default port. */
function resolveAccessLink(access: DeviceAccess, mgmtAddress: string, definition: DeviceAccessDefinition | undefined): ResolvedAccessLink | undefined {
  const link = definition && PROTOCOL_LINK[definition.protocol]
  const host = access.address || mgmtAddress
  if (!definition || !link || !host) {
    return undefined
  }
  const port = access.port ?? definition.defaultPort
  const portSuffix = port === link.standardPort ? "" : `:${port}`
  return {
    accessName: access.accessName ?? "",
    scheme: link.scheme,
    url: `${link.scheme}://${host}${portSuffix}`,
    priority: definition.priority,
  }
}

/**
 * Renders one link per access actually configured on the device (i.e. present in
 * `device.accesses`), pointing at `<scheme>://<host>:<port>` for that access, labeled
 * with its scheme prefix (ssh://, telnet://, http://, https://). Address/port overrides
 * on the access take precedence over the device's management address and the driver's
 * default port.
 */
export default function DeviceAccessLinks({ mgmtAddress, accesses, accessDefinitions }: DeviceAccessLinksProps) {
  const { t } = useTranslation()

  const links = (accesses ?? [])
    .map((access) => resolveAccessLink(access, mgmtAddress, accessDefinitions?.find((definition) => definition.name === access.accessName)))
    .filter((link): link is ResolvedAccessLink => !!link)
    .sort((a, b) => b.priority - a.priority)

  if (links.length === 0) {
    return null
  }

  return (
    <Stack direction="row" gap="1">
      {links.map((link) => (
        <Tooltip key={link.accessName} content={link.url}>
          <Button asChild variant="ghost" size="xs" aria-label={t("device.openAccessLink", { url: link.url })}>
            <a href={link.url} target="_blank" rel="noreferrer">
              {link.scheme}:
            </a>
          </Button>
        </Tooltip>
      ))}
    </Stack>
  )
}
