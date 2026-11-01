import withQuery, { WithQueryOptions } from "with-query"

import i18n from "@/i18n"

export enum HttpMethod {
  GET = "GET",
  POST = "POST",
  PUT = "PUT",
  DELETE = "DELETE",
  PATCH = "PATCH",
}

type HttpPayload<B> = {
  body?: B
  queryParams?: Record<string, unknown>
  queryParamsOptions?: WithQueryOptions
  headers?: Record<string, string>
  signal?: AbortSignal
}

type HttpClientOptions = {
  baseUrl?: string
  // Converts the ISO-8601 date strings of the JSON responses into Date objects
  reviveDates?: boolean
}

// An error about one parameter or property of the request (problem details)
export type NetshotFieldError = {
  field: string
  message: string
}

export type NetshotError = {
  title: string
  description: string
  code: NetshotErrorCode
  response?: Response
  fieldErrors?: NetshotFieldError[]
}

export enum NetshotErrorCode {
  GenericServer = -1,
  DeviceNotFound = 145,
  DiagnosticNotFound = 235,
  PolicyNotFound = 183,
  RuleNotFound = 193,
  ExpiredPassword = 205,
  FailedPasswordPolicy = 206,
}

export enum HttpStatus {
  Ok = 200,
  Created = 201,
  NoContent = 204,
  BadRequest = 400,
  Unauthorized = 401,
  Forbidden = 403,
  NotFound = 404,
  Conflict = 409,
  PreconditionFailed = 412,
  InternalServerError = 500,
  BadGateway = 502,
}

export enum HttpEventType {
  Forbidden = "forbidden",
  GatewayError = "gatewayError",
  Activity = "activity",
}

export type HttpEventCallback = (url: string, params: RequestInit, response: Response) => void

type ExecuteHttpEventCallbackConfig = {
  requestUrl: string
  requestOptions: RequestInit
  response: Response
}

export function isNetshotError(obj: unknown): obj is NetshotError {
  return (
    typeof obj === "object" &&
    obj !== null &&
    "title" in obj &&
    "description" in obj &&
    "code" in obj &&
    typeof (obj as NetshotError).title === "string" &&
    typeof (obj as NetshotError).description === "string"
  )
}

// A date as written by the API (UTC, with or without milliseconds)
const ISO_DATE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/

function reviveDate(_key: string, value: unknown) {
  return typeof value === "string" && ISO_DATE.test(value) ? new Date(value) : value
}

// Shared by the clients, so the screens register their callbacks once
const listeners = new Map<HttpEventType, HttpEventCallback>()

function createHttpClient(opts: HttpClientOptions = {}) {
  const { baseUrl = "", reviveDates = false } = opts

  async function readJson<R>(response: Response): Promise<R> {
    const text = await response.text()
    return JSON.parse(text, reviveDates ? reviveDate : undefined) as R
  }

  async function request<B = object>(
    method: HttpMethod,
    url: string,
    payload: HttpPayload<B> = {}
  ): Promise<Response> {
    const { body, queryParams, queryParamsOptions = {}, headers = {}, signal } = payload

    const requestUrl =
      baseUrl + (queryParams ? withQuery(url, queryParams, queryParamsOptions) : url)

    if (!headers["Accept"]) {
      headers["Accept"] = "application/json"
    }

    const requestOptions: RequestInit = {
      method,
      headers,
      signal,
    }

    if (typeof body === "object" && body !== null) {
      requestOptions.body = JSON.stringify(body as B)
      if (!headers["Content-Type"]) {
        headers["Content-Type"] = "application/json"
      }
    } else if (body && headers["Content-Type"]) {
      requestOptions.body = body as string
    }

    const response = await fetch(requestUrl, requestOptions)

    const isAuthError =
      response.status === HttpStatus.Forbidden || response.status === HttpStatus.Unauthorized

    if (!isAuthError) {
      // Any non-auth response counts as activity, used to (re)schedule the idle timeout warning
      executeHttpEventCallback(HttpEventType.Activity, { requestUrl, requestOptions, response })
    }

    if (!response.ok) {
      const callbackConfig = {
        requestUrl,
        requestOptions,
        response,
      }

      if (isAuthError) {
        executeHttpEventCallback(HttpEventType.Forbidden, callbackConfig)
      }

      if (response.status === HttpStatus.BadGateway) {
        executeHttpEventCallback(HttpEventType.GatewayError, callbackConfig)
      }

      try {
        const result = await response.json()

        if (response.headers.get("Content-Type")?.includes("problem+")) {
          // Problem details (RFC 9457)
          throw {
            title: result.title ?? i18n.t("common.error"),
            description: result.detail ?? result.title ?? i18n.t("common.netshotServerError"),
            code: result.errorCode ?? NetshotErrorCode.GenericServer,
            response,
            fieldErrors: result.errors ?? undefined,
          } as NetshotError
        }

        if (result.errorMsg) {
          throw {
            title: i18n.t("common.error"),
            description: i18n.t(result.errorMsg),
            code: result.errorCode,
            response,
          } as NetshotError
        }
      } catch (jsonErr) {
        if (typeof jsonErr.code !== "undefined") {
          throw jsonErr
        }
        throw {
          title: i18n.t("common.error"),
          description: i18n.t("common.netshotServerError"),
          code: NetshotErrorCode.GenericServer,
          response,
        } as NetshotError
      }
    }

    return response
  }

  function executeHttpEventCallback(
    eventType: HttpEventType,
    config: ExecuteHttpEventCallbackConfig
  ) {
    for (const [type, callback] of listeners) {
      if (type === eventType) {
        callback(config.requestUrl, config.requestOptions, config.response)
      }
    }
  }

  return {
    request,

    async get<R>(url: string, payload: HttpPayload<null> = {}) {
      const response = await request<null>(HttpMethod.GET, url, payload)
      try {
        return await readJson<R>(response)
      } catch (e) {
        console.error(e)
        return null
      }
    },

    async post<R, B>(url: string, body: B, meta: Omit<HttpPayload<B>, "body"> = {}) {
      const response = await request<B>(HttpMethod.POST, url, {
        ...meta,
        body,
      })
      try {
        return await readJson<R>(response)
      } catch (e) {
        console.error(e)
        return null
      }
    },

    async put<R, B>(url: string, body: B, meta: Omit<HttpPayload<B>, "body"> = {}) {
      const response = await request<B>(HttpMethod.PUT, url, {
        ...meta,
        body,
      })
      try {
        return await readJson<R>(response)
      } catch (e) {
        console.error(e)
        return null
      }
    },

    async patch<R, B>(url: string, body: B, meta: HttpPayload<B> = {}) {
      const response = await request<B>(HttpMethod.PATCH, url, {
        ...meta,
        body,
      })
      try {
        return await readJson<R>(response)
      } catch (e) {
        console.error(e)
        return null
      }
    },

    async delete(url: string, payload: HttpPayload<null> = {}) {
      await request<null>(HttpMethod.DELETE, url, payload)
    },

    // GET with the ETag of the answer (the version of the object, for If-Match)
    async getWithETag<R>(url: string, payload: HttpPayload<null> = {}) {
      const response = await request<null>(HttpMethod.GET, url, payload)
      return { data: await readJson<R>(response), etag: response.headers.get("ETag") }
    },

    on(type: HttpEventType, callback: HttpEventCallback) {
      listeners.set(type, callback)
    },

    off(type: HttpEventType) {
      listeners.delete(type)
    },
  }
}

export { createHttpClient }

export default createHttpClient({
  baseUrl: "/api",
})
