const DOCS_BASE_URL = "https://docs.netshot.net"

export function getUserGuideUrl(serverVersion?: string) {
  if (!serverVersion || serverVersion.match(/-dev/)) {
    return `${DOCS_BASE_URL}/dev/`
  }
  return `${DOCS_BASE_URL}/v${serverVersion}/`
}
