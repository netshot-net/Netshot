import { Button, Icon, Menu, Portal } from "@chakra-ui/react"
import { useTranslation } from "react-i18next"
import { LuChevronDown } from "react-icons/lu"

import { useLanguageOptions } from "@/i18n"

export function LanguageMenuItems() {
  const { i18n } = useTranslation()
  const { options } = useLanguageOptions()

  return (
    <>
      {options.map((option) => (
        <Menu.CheckboxItem
          key={option.value}
          value={option.value}
          checked={i18n.resolvedLanguage === option.value}
          onCheckedChange={() => i18n.changeLanguage(option.value)}
        >
          <Menu.ItemIndicator />
          {option.flag}
          {option.label}
        </Menu.CheckboxItem>
      ))}
    </>
  )
}

export default function LanguageMenu() {
  const { i18n } = useTranslation()
  const { options } = useLanguageOptions()

  const currentOption = options.find((option) => option.value === i18n.resolvedLanguage) ?? options[0]

  return (
    <Menu.Root positioning={{ placement: "bottom-end" }}>
      <Menu.Trigger asChild>
        <Button variant="ghost" size="sm" px="2" gap="1">
          {currentOption?.flag}
          <Icon><LuChevronDown /></Icon>
        </Button>
      </Menu.Trigger>
      <Portal>
        <Menu.Positioner>
          <Menu.Content>
            <LanguageMenuItems />
          </Menu.Content>
        </Menu.Positioner>
      </Portal>
    </Menu.Root>
  )
}
