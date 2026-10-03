import { AttributeOption, AttributeType, OperatorOption, OperatorType } from "./types"
import { useOperatorMapping } from "./useOperatorMapping"

export function useQueryBuilderOperator() {
  const mapping = useOperatorMapping()

  function getAllOptionForText(attributeName: string) {
    return [
      {
        ...mapping[OperatorType.Is],
        callback: () => `[${attributeName}] is "text"`,
      },
      {
        ...mapping[OperatorType.Contains],
        callback: () => `[${attributeName}] contains "text"`,
      },
      {
        ...mapping[OperatorType.ContainsNoCase],
        callback: () => `[${attributeName}] containsnocase "text"`,
      },
      {
        ...mapping[OperatorType.StartsWith],
        callback: () => `[${attributeName}] startswith "text"`,
      },
      {
        ...mapping[OperatorType.EndsWith],
        callback: () => `[${attributeName}] endswith "text"`,
      },
      {
        ...mapping[OperatorType.Matches],
        callback: () => `[${attributeName}] matches "pattern"`,
      },
    ]
  }

  function getAllOptionForNumeric(attributeName: string) {
    return [
      {
        ...mapping[OperatorType.Is],
        callback: () => `[${attributeName}] is 16`,
      },
      {
        ...mapping[OperatorType.LessThan],
        callback: () => `[${attributeName}] lessthan 16`,
      },
      {
        ...mapping[OperatorType.GreaterThan],
        callback: () => `[${attributeName}] greaterthan 16`,
      },
    ]
  }

  function getAllOptionForId(attributeName: string) {
    return [
      {
        ...mapping[OperatorType.Is],
        callback: () => `[${attributeName}] is 42`,
      },
    ]
  }

  function getAllOptionForDate(attributeName: string) {
    return [
      {
        ...mapping[OperatorType.Is],
        callback: () => `[${attributeName}] is "2023-01-01"`,
      },
      {
        ...mapping[OperatorType.Before],
        callback: () => `[${attributeName}] before "2023-01-01"`,
      },
      {
        ...mapping[OperatorType.After],
        callback: () => `[${attributeName}] after "2023-01-01"`,
      },
      {
        ...mapping[OperatorType.BeforeRelative],
        callback: () => `[${attributeName}] before "now -1d"`,
      },
    ]
  }

  function getAllOptionForIpAddress(attributeName: string) {
    return [
      {
        ...mapping[OperatorType.Is],
        callback: () => `[${attributeName}] is 16.16.16.16`,
      },
      {
        ...mapping[OperatorType.In],
        callback: () => `[${attributeName}] in 16.16.0.0/16`,
      },
    ]
  }

  function getAllOptionForMacAddress(attributeName: string) {
    return [
      {
        ...mapping[OperatorType.Is],
        callback: () => `[${attributeName}] is 1616.1616.1616`,
      },
      {
        ...mapping[OperatorType.In],
        callback: () => `[${attributeName}] in 1616.1616.1616/32`,
      },
    ]
  }

  function getAllOptionForBinary(attributeName: string) {
    return [
      {
        ...mapping[OperatorType.True],
        callback: () => `[${attributeName}] is true`,
      },
      {
        ...mapping[OperatorType.False],
        callback: () => `[${attributeName}] is false`,
      },
    ]
  }

  function getAllOptionByAttribute(attribute: AttributeOption["value"]): OperatorOption[] {
    if (!attribute) {
      return []
    }

    if (
      attribute.type === AttributeType.Text ||
      attribute.type === AttributeType.LongText ||
      attribute.type === AttributeType.Tag
    ) {
      return getAllOptionForText(attribute.name)
    } else if (attribute.type === AttributeType.Numeric) {
      return getAllOptionForNumeric(attribute.name)
    } else if (attribute.type === AttributeType.Id) {
      return getAllOptionForId(attribute.name)
    } else if (attribute.type === AttributeType.Date) {
      return getAllOptionForDate(attribute.name)
    } else if (attribute.type === AttributeType.IpAddress) {
      return getAllOptionForIpAddress(attribute.name)
    } else if (attribute.type === AttributeType.MacAddress) {
      return getAllOptionForMacAddress(attribute.name)
    } else if (attribute.type === AttributeType.Binary) {
      return getAllOptionForBinary(attribute.name)
    } else {
      return []
    }
  }

  return {
    getAllOptionByAttribute,
    getAllOptionForText,
  }
}
