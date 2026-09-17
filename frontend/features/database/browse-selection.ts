import type { TreeSelection } from './schema-tree'

export function boundTreeSelection(
  selection: TreeSelection | null,
  boundInstanceId: string,
  selectedId: string,
): TreeSelection | null {
  if (!selectedId || boundInstanceId !== selectedId) {
    return null
  }
  return selection
}
