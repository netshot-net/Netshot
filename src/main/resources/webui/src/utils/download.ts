export async function download(blob: Blob, filename: string) {
  const href = URL.createObjectURL(blob)
  const a = document.createElement("a")

  Object.assign(a.style, {
    position: "fixed",
    top: "-9999px",
    left: "-9999px",
    opacity: 0,
  })

  a.href = href
  a.download = filename
  document.body.appendChild(a)

  a.click()
  a.remove()

  URL.revokeObjectURL(href)
}
