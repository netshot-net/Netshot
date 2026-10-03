import { BadgeProps, Icon, Span } from "@chakra-ui/react"
import { type Ref } from "react"
import { LuTag } from "react-icons/lu"
import { Link } from "react-router"
import IconBadge from "../IconBadge"

export type DeviceTagBadgeProps = BadgeProps & {
  tag: string
  /** Link to the device search for this tag (default: true). */
  withLink?: boolean
  ref?: Ref<HTMLSpanElement>
}

/** Builds the device finder query to look for devices having the given tag. */
export function buildDeviceTagQuery(tag: string) {
  return `[Tag] is "${tag.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`
}

function DeviceTagBadge({ tag, withLink = true, ref, ...rest }: DeviceTagBadgeProps) {
  const content = (
    <>
      <Icon size="sm" flexShrink={0}>
        <LuTag />
      </Icon>
      <Span minW="0" overflow="hidden" textOverflow="ellipsis" whiteSpace="nowrap">
        {tag}
      </Span>
    </>
  )

  if (!withLink) {
    return (
      <IconBadge ref={ref} {...rest}>
        {content}
      </IconBadge>
    )
  }

  return (
    <IconBadge ref={ref} {...rest} asChild>
      <Link to={`/app/devices?q=${encodeURIComponent(buildDeviceTagQuery(tag))}`}>{content}</Link>
    </IconBadge>
  )
}

export default DeviceTagBadge
