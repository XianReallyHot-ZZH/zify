/**
 * 将对象序列化为 URL 查询字符串。
 * 自动过滤 undefined 和 null 值。
 * 数组值会重复键名（如 a=1&a=2）。
 */
export function toQueryString(
  params: Record<string, string | number | boolean | undefined | null>,
): string {
  const entries: string[] = []

  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue
    entries.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
  }

  return entries.join('&')
}
