import { Option } from "@/types"
import { TFunction } from "i18next"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import type { TranslationKey } from "@/i18n/tKey"

export type CreateOptionHookConfig<T> = {
  translate?: boolean
  translateFn?: (option: T, t: TFunction) => T
}

export function createOptionHook<T extends Option<unknown, unknown>>(
  options: T[],
  config?: CreateOptionHookConfig<T>
) {
  return function useOptions() {
    const { t } = useTranslation()
    const { translate = true, translateFn } = config ?? {}

    const translatedOptions = useMemo(() => {
      if (!translate) {
        return options
      }

      return options.map((opt) => {
        if (translateFn) {
          return translateFn(opt, t)
        }

        return {
          ...opt,
          // The keys are checked where the options are declared (tKey)
          label: t(opt.label as TranslationKey),
          ...(opt.description ? { description: t(opt.description as TranslationKey) } : {}),
        }
      })
    }, [t, translate, translateFn])

    function getByValue(value: T["value"]) {
      return translatedOptions.find((option) => option.value === value)
    }

    function getByLabel(label: T["label"]) {
      return translatedOptions.find((option) => option.label === label)
    }

    function getLabelByValue(value: T["value"]) {
      return translatedOptions.find((option) => option.value === value)?.label as string
    }

    function getFirst() {
      return translatedOptions[0]
    }

    return {
      options: translatedOptions,
      getByLabel,
      getByValue,
      getLabelByValue,
      getFirst,
    }
  }
}
