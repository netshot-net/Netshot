import { User } from "@/types";
import { toUser } from "./auth";
import { HttpMethod, HttpStatus, isNetshotError } from "./httpClient";
import oidc from "./oidc";
import { UpdateUserPayload } from "./types";
import { client, Schemas } from "./client";

export type OidcInfo = {
  endpoint: string | null;
  clientId: string | null;
};

export type MeResult = {
  user: User | null;
  oidcInfo: OidcInfo;
};

async function me(): Promise<MeResult> {
  try {
    const response = await client.request(HttpMethod.GET, "/auth/me");
    const oidcInfo = oidc.extractInfoFromHeaders(response.headers);
    const user = toUser(await response.json() as Schemas["CurrentUser"]);
    return {
      user,
      oidcInfo,
    };
  } catch (err) {
    if (
      isNetshotError(err) &&
      err.response &&
      (err.response.status === HttpStatus.Unauthorized ||
        err.response.status === HttpStatus.Forbidden)
    ) {
      return {
        user: null,
        oidcInfo: oidc.extractInfoFromHeaders(err.response.headers),
      };
    }
    throw err;
  }
}

/**
 * Changes the password of the current (local) user.
 */
async function update(payload: UpdateUserPayload) {
  const user = await client.patch<Schemas["CurrentUser"], Schemas["PasswordChangeRequest"]>("/auth/me", {
    currentPassword: payload.password,
    newPassword: payload.newPassword,
  });
  return toUser(user ?? undefined);
}

export default {
  me,
  update,
};
