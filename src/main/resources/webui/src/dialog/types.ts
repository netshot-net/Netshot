import { DialogRootProps } from "@chakra-ui/react"
import { ComponentProps, FunctionComponent, ReactElement } from "react"

export type PromiseOrVoid = Promise<void> | void

export type BaseDialogProps = {
  title?: string | ReactElement
  description?: string | ReactElement | (() => ReactElement)
  isLoading?: boolean
  onCancel?(): PromiseOrVoid
  isOpen?: boolean
  isCentered?: boolean
  size?: DialogRootProps["size"]
  variant?: DialogRootProps["variant"]
  hideFooter?: boolean
}

export type DialogConfig<P extends BaseDialogProps = BaseDialogProps> = {
  id: string
  component: FunctionComponent<P>
  props: P
  update(config: ComponentProps<FunctionComponent<P>>): void
  open(): void
  // Resolves once the dialog has actually been torn down (its close animation has
  // finished and `remove()` has run) -- not merely once it starts closing -- so callers
  // can safely open a replacement dialog right after without both being briefly
  // registered together in Ark/Zag's dismissable-layer stack.
  close(): Promise<void>
  remove(): void
  onClose(cb: () => void): void
}

export type DialogContextProps = {
  add<P extends BaseDialogProps>(key: string, component: FunctionComponent<P>): void
  remove(key: string): void
  update(key: string, config: Partial<DialogConfig<BaseDialogProps>>): void
  updateProps<P extends BaseDialogProps>(key: string, props: P): void
}
