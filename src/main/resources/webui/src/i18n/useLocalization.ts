import {
  type CalendarDateTime,
  DateFormatter,
  fromAbsolute,
  fromDate,
  toCalendarDate,
  toCalendarDateTime,
  type DateValue,
} from "@internationalized/date"
import { useCallback, useMemo } from "react"
import { useTranslation } from "react-i18next"
import { useTimezone } from "./LocalizationContext"

const PLACEHOLDER_REF_DATE = new Date(2025, 10, 23, 15, 7, 8)

export function useLocalization() {
  const { i18n, t } = useTranslation()
  const { timezone } = useTimezone()

  const formatNumber = useCallback(
    (value: string) => {
      return new Intl.NumberFormat(i18n.language).format(+value)
    },
    [i18n]
  )

  const dateFormatter = useMemo(() => {
    return new DateFormatter(i18n.language, {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      timeZone: timezone,
    })
  }, [i18n.language, timezone])

  const formatDate = useCallback((date: number | Date) => {
    return dateFormatter.format(date instanceof Date ? date : new Date(date))
  }, [dateFormatter])

  const dayMonthFormatter = useMemo(() => {
    return new DateFormatter(i18n.language, {
      day: "2-digit",
      month: "short",
      timeZone: timezone,
    })
  }, [i18n.language, timezone])

  const formatDayMonth = useCallback((date: number | Date) => {
    return dayMonthFormatter.format(date instanceof Date ? date : new Date(date))
  }, [dayMonthFormatter])

  const monthYearFormatter = useMemo(() => {
    return new DateFormatter(i18n.language, {
      month: "short",
      year: "numeric",
      timeZone: timezone,
    })
  }, [i18n.language, timezone])

  const formatMonthYear = useCallback((date: number | Date) => {
    return monthYearFormatter.format(date instanceof Date ? date : new Date(date))
  }, [monthYearFormatter])

  const hourMinuteFormatter = useMemo(() => {
    return new DateFormatter(i18n.language, {
      hour: "2-digit",
      minute: "2-digit",
      timeZone: timezone,
    })
  }, [i18n.language, timezone])

  const formatHourMinute = useCallback((date: number | Date) => {
    return hourMinuteFormatter.format(date instanceof Date ? date : new Date(date))
  }, [hourMinuteFormatter])

  const dayMonthHourMinuteFormatter = useMemo(() => {
    return new DateFormatter(i18n.language, {
      month: "short",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      timeZone: timezone,
    })
  }, [i18n.language, timezone])

  const formatDayMonthHourMinute = useCallback((date: number | Date) => {
    return dayMonthHourMinuteFormatter.format(date instanceof Date ? date : new Date(date))
  }, [dayMonthHourMinuteFormatter])

  const dateTimeFormatter = useMemo(() => {
    return new DateFormatter(i18n.language, {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      timeZone: timezone,
    })
  }, [i18n.language, timezone])

  const formatDateTime = useCallback((date: number | Date) => {
    return dateTimeFormatter.format(date instanceof Date ? date : new Date(date))
  }, [dateTimeFormatter])

  const relativeTimeFormatter = useMemo(() => {
    return new Intl.RelativeTimeFormat(i18n.language, { numeric: "auto" })
  }, [i18n.language])

  const formatRelativeTime = useCallback((date: number | Date) => {
    const time = date instanceof Date ? date.getTime() : date
    const diffSeconds = Math.round((time - Date.now()) / 1000)
    const absSeconds = Math.abs(diffSeconds)

    if (absSeconds < 60) return relativeTimeFormatter.format(diffSeconds, "second")
    const diffMinutes = Math.round(diffSeconds / 60)
    if (Math.abs(diffMinutes) < 60) return relativeTimeFormatter.format(diffMinutes, "minute")
    const diffHours = Math.round(diffMinutes / 60)
    if (Math.abs(diffHours) < 24) return relativeTimeFormatter.format(diffHours, "hour")
    const diffDays = Math.round(diffHours / 24)
    return relativeTimeFormatter.format(diffDays, "day")
  }, [relativeTimeFormatter])

  const datePlaceholder = useMemo(() => {
    const letters: Partial<Record<Intl.DateTimeFormatPartTypes, string>> = {
      year: t("common.date.yearLetter"),
      month: t("common.date.monthLetter"),
      day: t("common.date.dayLetter"),
    }
    return dateFormatter
      .formatToParts(PLACEHOLDER_REF_DATE)
      .map((part) => {
        const letter = letters[part.type]
        return letter ? letter.repeat(part.value.length) : part.value
      })
      .join("")
  }, [dateFormatter, t])

  const dateTimePlaceholder = useMemo(() => {
    const letters: Partial<Record<Intl.DateTimeFormatPartTypes, string>> = {
      year: t("common.date.yearLetter"),
      month: t("common.date.monthLetter"),
      day: t("common.date.dayLetter"),
      hour: t("common.date.hourLetter"),
      minute: t("common.date.minuteLetter"),
      second: t("common.date.secondLetter"),
    }
    return dateTimeFormatter
      .formatToParts(PLACEHOLDER_REF_DATE)
      .map((part) => {
        const letter = letters[part.type]
        return letter ? letter.repeat(part.value.length) : part.value
      })
      .join("")
  }, [dateTimeFormatter, t])

  const startOfDay = useCallback((date: number | Date): number => {
    const zdt = date instanceof Date ? fromDate(date, timezone) : fromAbsolute(date, timezone)
    return zdt.set({ hour: 0, minute: 0, second: 0, millisecond: 0 }).toDate().getTime()
  }, [timezone])

  const startOfNextDay = useCallback((date: number | Date): number => {
    const zdt = date instanceof Date ? fromDate(date, timezone) : fromAbsolute(date, timezone)
    return zdt.add({ days: 1 }).set({ hour: 0, minute: 0, second: 0, millisecond: 0 }).toDate().getTime()
  }, [timezone])

  const calendarDateToTimestamp = useCallback((date: DateValue): number => {
    return date.toDate(timezone).getTime()
  }, [timezone])

  const numberToCalendarDate = useCallback((timestamp: number | undefined | null): DateValue | null => {
    if (timestamp == null || !Number.isFinite(timestamp)) return null
    return toCalendarDate(fromAbsolute(timestamp, timezone))
  }, [timezone])

  const calendarDateTimeToTimestamp = useCallback((value: CalendarDateTime): number => {
    return value.toDate(timezone).getTime()
  }, [timezone])

  const numberToCalendarDateTime = useCallback(
    (timestamp: number | undefined | null): CalendarDateTime | null => {
      if (timestamp == null || !Number.isFinite(timestamp)) return null
      return toCalendarDateTime(fromAbsolute(timestamp, timezone))
    },
    [timezone]
  )

  return {
    timezone,
    formatNumber,
    formatDate,
    formatDayMonth,
    formatMonthYear,
    formatDateTime,
    formatRelativeTime,
    formatHourMinute,
    formatDayMonthHourMinute,
    datePlaceholder,
    dateTimePlaceholder,
    startOfDay,
    startOfNextDay,
    calendarDateToTimestamp,
    numberToCalendarDate,
    calendarDateTimeToTimestamp,
    numberToCalendarDateTime,
  }
}
