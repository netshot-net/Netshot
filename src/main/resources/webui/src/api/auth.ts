import { User } from "@/types"
import { client, Schemas } from "./client"
import { ServerInfoResponse, SigninPayload } from "./types"

/**
 * Maps the current user of the API to the user of the web UI.
 */
export function toUser(user?: Schemas["CurrentUser"] | null): User {
  return {
    id: user?.id ?? 0,
    level: user?.level ?? 0,
    local: user?.local ?? false,
    username: user?.username ?? user?.display ?? "",
  }
}

/**
 * Netshot server info (version, session timeout)
 */
async function serverInfo(): Promise<ServerInfoResponse> {
  const info = await client.get<Schemas["SystemInfo"]>("/system/info")
  return {
    serverVersion: info?.version ?? "",
    maxIdleTimeout: info?.sessionMaxIdleTime ?? 0,
  }
}

/**
 * Sign in user (with a new password when the current one has expired)
 */
async function signin(payload: SigninPayload & { newPassword?: string }) {
  const { newPassword, ...credentials } = payload
  const request: Schemas["SessionRequest"] = newPassword ? { ...credentials, newPassword } : credentials
  const session = await client.post<Schemas["Session"], Schemas["SessionRequest"]>("/auth/session", request)
  return toUser(session?.user)
}

/**
 * Sign in with oidc
 */
async function signinWithOidc(authorizationCode: string, redirectUri: string) {
  const session = await client.post<Schemas["Session"], Schemas["SessionRequest"]>("/auth/session", {
    authorizationCode,
    redirectUri,
  })
  return toUser(session?.user)
}

/**
 * Sign out current user
 */
async function signout() {
  return client.delete("/auth/session")
}

export default {
  serverInfo,
  signin,
  signinWithOidc,
  signout,
}
