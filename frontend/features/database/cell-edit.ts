export function shouldCommitCell(draft: string, stored: unknown): boolean {
  if (stored == null) {
    return draft !== '' && draft !== 'null'
  }
  return draft !== String(stored)
}
