import { TaskScheduleType } from "@/types"
import { fromAbsolute } from "@internationalized/date"

type ParentedTask = {
  id: number
  parentTaskId?: number
  childOrder?: number
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
    if (row.parentTaskId && idsPresent.has(row.parentTaskId)) {
      const children = childrenByParent.get(row.parentTaskId) ?? []
      children.push(row)
      childrenByParent.set(row.parentTaskId, children)
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
    if (!row.parentTaskId || !idsPresent.has(row.parentTaskId)) {
      appendWithChildren(row, 0)
    }
  }

  return result
}

/**
 * True for schedule types that trigger more than once (as opposed to a single
 * one-off run, immediate or "at" a given time).
 */
export function isRepeatingSchedule(scheduleType: TaskScheduleType): boolean {
  return scheduleType !== TaskScheduleType.Asap && scheduleType !== TaskScheduleType.At
}

/**
 * Mirrors the backend's Task#getNextExecutionDate(): the next occurrence at or after
 * "now + 1 minute", found by stepping scheduleReference forward by scheduleFactor
 * units (hour/day/week/month) until it clears that cutoff. Returns null for ASAP
 * (no fixed date) and scheduleReference itself for a one-off AT schedule.
 */
export function computeNextExecutionDate(
  scheduleReference: number,
  scheduleType: TaskScheduleType,
  scheduleFactor: number
): number | null {
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

const MINUTE = 60_000
const HOUR = 3_600_000
const DAY = 86_400_000

export type BucketLabelPrecision = "minute" | "hour" | "day"

export type BucketUnit = {
  ms: number
  labelPrecision: BucketLabelPrecision
}

/**
 * Fixed-length bucket durations to snap the histogram to, from a minute up to a year.
 * Kept as constant durations (rather than true calendar months/years, which vary in
 * length) so bucket boundaries can be computed with plain arithmetic.
 */
const BUCKET_UNITS: BucketUnit[] = [
  { ms: MINUTE, labelPrecision: "minute" },
  { ms: 2 * MINUTE, labelPrecision: "minute" },
  { ms: 5 * MINUTE, labelPrecision: "minute" },
  { ms: 10 * MINUTE, labelPrecision: "minute" },
  { ms: 15 * MINUTE, labelPrecision: "minute" },
  { ms: 30 * MINUTE, labelPrecision: "minute" },
  { ms: HOUR, labelPrecision: "hour" },
  { ms: 2 * HOUR, labelPrecision: "hour" },
  { ms: 3 * HOUR, labelPrecision: "hour" },
  { ms: 6 * HOUR, labelPrecision: "hour" },
  { ms: 12 * HOUR, labelPrecision: "hour" },
  { ms: DAY, labelPrecision: "day" },
  { ms: 2 * DAY, labelPrecision: "day" },
  { ms: 3 * DAY, labelPrecision: "day" },
  { ms: 7 * DAY, labelPrecision: "day" },
  { ms: 14 * DAY, labelPrecision: "day" },
  { ms: 30 * DAY, labelPrecision: "day" },
  { ms: 90 * DAY, labelPrecision: "day" },
  { ms: 180 * DAY, labelPrecision: "day" },
  { ms: 365 * DAY, labelPrecision: "day" },
]

export const TARGET_BUCKET_COUNT = 24
export const MIN_BUCKET_COUNT = 18
export const MAX_BUCKET_COUNT = 30

/**
 * Picks the fixed-length unit (from `BUCKET_UNITS`) whose bucket count for the given
 * span is closest to `target`, preferring one within [min, max] when possible. For
 * spans much longer than the largest available unit covers (e.g. "All time" on an
 * old install), this falls back to the largest unit even if the resulting count
 * exceeds `max`.
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

/**
 * Snaps a timestamp down to the nearest natural boundary for the given bucket unit
 * (top of the minute/hour, or local midnight), in the given IANA timezone.
 */
export function snapToBucketBoundary(t: number, unit: BucketUnit, timezone: string): number {
  const zdt = fromAbsolute(t, timezone)

  if (unit.ms < HOUR) {
    const unitMinutes = unit.ms / MINUTE
    const minute = Math.floor(zdt.minute / unitMinutes) * unitMinutes
    return zdt.set({ minute, second: 0, millisecond: 0 }).toDate().getTime()
  }

  if (unit.ms < DAY) {
    const unitHours = unit.ms / HOUR
    const hour = Math.floor(zdt.hour / unitHours) * unitHours
    return zdt.set({ hour, minute: 0, second: 0, millisecond: 0 }).toDate().getTime()
  }

  return zdt.set({ hour: 0, minute: 0, second: 0, millisecond: 0 }).toDate().getTime()
}

export type BucketPlan = {
  from: number
  to: number
  buckets: number
  unit: BucketUnit
}

/**
 * Resolves a requested [from, to] window into a bucket plan snapped to natural time
 * divisions: picks a nice fixed-length unit close to `TARGET_BUCKET_COUNT` buckets,
 * snaps `from` down to a boundary of that unit, and extends `to` up to the next
 * boundary so the whole originally requested window is fully covered.
 */
export function planBuckets(from: number, to: number, timezone: string): BucketPlan {
  const span = Math.max(1, to - from)
  const unit = pickBucketUnit(span)
  const snappedFrom = snapToBucketBoundary(from, unit, timezone)
  const buckets = Math.max(1, Math.ceil((to - snappedFrom) / unit.ms))
  const snappedTo = snappedFrom + buckets * unit.ms

  return { from: snappedFrom, to: snappedTo, buckets, unit }
}
