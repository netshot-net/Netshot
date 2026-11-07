import { TaskScheduleType } from "@/types"
import { fromAbsolute, startOfMonth, startOfWeek, type ZonedDateTime } from "@internationalized/date"

type ParentedTask = {
  id: number
  parentTask?: { id: number } | null
  childOrder?: number | null
}

export type TaskTreeRow<T> = T & { depth: number }

/**
 * Reorders a task list so each child task is placed right after its parent
 * (recursively, for chained tasks), instead of wherever its own date would
 * otherwise sort it, and annotates each row with its nesting `depth` (0 for
 * root tasks) so callers can indent child rows. Root-level tasks (no parent,
 * or a parent absent from `rows` -- e.g. a children-only listing) keep their
 * relative order; each parent's children are grouped by `childOrder`.
 */
export function buildTaskTree<T extends ParentedTask>(rows: T[]): TaskTreeRow<T>[] {
  const idsPresent = new Set(rows.map((row) => row.id))
  const childrenByParent = new Map<number, T[]>()

  for (const row of rows) {
    const parentId = row.parentTask?.id
    if (parentId && idsPresent.has(parentId)) {
      const children = childrenByParent.get(parentId) ?? []
      children.push(row)
      childrenByParent.set(parentId, children)
    }
  }
  for (const children of childrenByParent.values()) {
    children.sort((a, b) => (a.childOrder ?? 0) - (b.childOrder ?? 0))
  }

  const visited = new Set<number>()
  const result: TaskTreeRow<T>[] = []

  function appendWithChildren(row: T, depth: number) {
    if (visited.has(row.id)) return
    visited.add(row.id)
    result.push({ ...row, depth })
    for (const child of childrenByParent.get(row.id) ?? []) {
      appendWithChildren(child, depth + 1)
    }
  }

  for (const row of rows) {
    if (!row.parentTask?.id || !idsPresent.has(row.parentTask.id)) {
      appendWithChildren(row, 0)
    }
  }

  return result
}

/**
 * True for schedule types that trigger more than once (as opposed to a single
 * one-off run, immediate or "at" a given time).
 */
export function isRepeatingSchedule(scheduleType: string | null): boolean {
  return scheduleType != null && scheduleType !== TaskScheduleType.Asap && scheduleType !== TaskScheduleType.At
}

/**
 * Mirrors the backend's Task#getNextExecutionDate(): the next occurrence at or after
 * "now + 1 minute", found by stepping scheduleReference forward by scheduleFactor
 * units (hour/day/week/month) until it clears that cutoff. Returns null for ASAP
 * (no fixed date) and scheduleReference itself for a one-off AT schedule.
 */
export function computeNextExecutionDate(
  scheduleReference: number,
  scheduleType: string | null,
  scheduleFactor: number
): number | null {
  if (scheduleType == null) return null
  if (scheduleType === TaskScheduleType.Asap) return null
  if (scheduleType === TaskScheduleType.At) return scheduleReference

  const factor = scheduleFactor > 0 ? scheduleFactor : 1
  const cutoff = Date.now() + 60_000
  const next = new Date(scheduleReference)
  const cutoffYear = new Date(cutoff).getFullYear()
  if (next.getFullYear() < cutoffYear) {
    next.setFullYear(cutoffYear - 1)
  }

  for (let i = 0; i < 100_000; i++) {
    if (next.getTime() > cutoff) return next.getTime()
    switch (scheduleType) {
      case TaskScheduleType.Hourly:
        next.setHours(next.getHours() + factor)
        break
      case TaskScheduleType.Daily:
        next.setDate(next.getDate() + factor)
        break
      case TaskScheduleType.Weekly:
        next.setDate(next.getDate() + factor * 7)
        break
      case TaskScheduleType.Monthly:
        next.setMonth(next.getMonth() + factor)
        break
    }
  }
  return next.getTime()
}

const HOUR = 3_600_000
const DAY = 86_400_000

/** The sizes of the time buckets of the task statistics. */
export type BucketInterval = "hour" | "day" | "week" | "month"

export type BucketLabelPrecision = "hour" | "day"

export type BucketUnit = {
  interval: BucketInterval
  /** An approximate duration, to pick the interval of a time range (the real buckets follow the calendar). */
  ms: number
  labelPrecision: BucketLabelPrecision
}

/** The bucket sizes of the statistics, from an hour up to a month. */
const BUCKET_UNITS: BucketUnit[] = [
  { interval: "hour", ms: HOUR, labelPrecision: "hour" },
  { interval: "day", ms: DAY, labelPrecision: "day" },
  { interval: "week", ms: 7 * DAY, labelPrecision: "day" },
  { interval: "month", ms: 30 * DAY, labelPrecision: "day" },
]

export const TARGET_BUCKET_COUNT = 24
export const MIN_BUCKET_COUNT = 12
export const MAX_BUCKET_COUNT = 36

/**
 * Picks the bucket size whose bucket count for the given span is closest to `target`, preferring one within
 * [min, max] when possible.
 */
export function pickBucketUnit(
  span: number,
  target = TARGET_BUCKET_COUNT,
  min = MIN_BUCKET_COUNT,
  max = MAX_BUCKET_COUNT
): BucketUnit {
  const candidates = BUCKET_UNITS.map((unit) => ({ unit, count: span / unit.ms }))
  const inRange = candidates.filter((c) => c.count >= min && c.count <= max)
  const pool = inRange.length > 0 ? inRange : candidates
  return pool.reduce((best, c) =>
    Math.abs(c.count - target) < Math.abs(best.count - target) ? c : best
  ).unit
}

/** The start of the bucket (hour, day, Monday-based week or month) containing the date, in the time zone. */
function startOfBucket(zdt: ZonedDateTime, interval: BucketInterval): ZonedDateTime {
  const hour = zdt.set({ minute: 0, second: 0, millisecond: 0 })
  if (interval === "hour") return hour
  const day = hour.set({ hour: 0 })
  if (interval === "day") return day
  if (interval === "week") return startOfWeek(day, "en-GB", "mon").set({ hour: 0 })
  return startOfMonth(day).set({ hour: 0 })
}

function nextBucket(zdt: ZonedDateTime, interval: BucketInterval): ZonedDateTime {
  switch (interval) {
    case "hour":
      return zdt.add({ hours: 1 })
    case "day":
      return zdt.add({ days: 1 })
    case "week":
      return zdt.add({ weeks: 1 })
    default:
      return zdt.add({ months: 1 })
  }
}

/**
 * Snaps a timestamp down to the start of its bucket (top of the hour, local midnight, Monday, first day of the month),
 * in the given IANA timezone: the boundaries the statistics of the API use.
 */
export function snapToBucketBoundary(t: number, unit: BucketUnit, timezone: string): number {
  return startOfBucket(fromAbsolute(t, timezone), unit.interval).toDate().getTime()
}

export type BucketPlan = {
  from: number
  to: number
  unit: BucketUnit
}

/**
 * Resolves a requested [from, to] window into a bucket plan aligned on the buckets of the API: picks a bucket size
 * giving close to `TARGET_BUCKET_COUNT` buckets, snaps `from` down to the start of its bucket and extends `to` up to
 * the next boundary, so the whole originally requested window is covered.
 */
export function planBuckets(from: number, to: number, timezone: string): BucketPlan {
  const span = Math.max(1, to - from)
  const unit = pickBucketUnit(span)
  const start = startOfBucket(fromAbsolute(from, timezone), unit.interval)
  const toZoned = fromAbsolute(Math.max(to, from + 1), timezone)
  let end = startOfBucket(toZoned, unit.interval)
  if (end.toDate().getTime() < to) {
    end = nextBucket(end, unit.interval)
  }
  return { from: start.toDate().getTime(), to: end.toDate().getTime(), unit }
}

/**
 * Lists the buckets of a plan, aligned like the ones of the API (same time zone): every bucket of the range, whether it
 * has tasks or not.
 */
export function listBuckets(plan: BucketPlan, timezone: string): { from: number; to: number }[] {
  const buckets: { from: number; to: number }[] = []
  let cursor = fromAbsolute(plan.from, timezone)
  while (cursor.toDate().getTime() < plan.to && buckets.length < 1000) {
    const next = nextBucket(cursor, plan.unit.interval)
    buckets.push({ from: cursor.toDate().getTime(), to: next.toDate().getTime() })
    cursor = next
  }
  return buckets
}
