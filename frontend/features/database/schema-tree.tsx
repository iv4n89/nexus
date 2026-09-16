'use client'

import type { DatabaseMetadata } from '@/types/api'

export type TreeSelection =
  | { kind: 'sql'; schema: string; table: string; primaryKey: string[] }
  | { kind: 'mongo'; database: string; collection: string }

export function SchemaTree({
  metadata,
  selected,
  onSelect,
}: {
  metadata: DatabaseMetadata
  selected: TreeSelection | null
  onSelect: (selection: TreeSelection) => void
}) {
  if (metadata.engine === 'MONGO') {
    return (
      <ul className="font-mono text-sm">
        {(metadata.databases ?? []).map((database) => (
          <li key={database.name} className="mb-3">
            <div className="text-[#888]">{database.name}</div>
            <ul className="mt-1 pl-3">
              {database.collections.map((collection) => {
                const active =
                  selected?.kind === 'mongo' &&
                  selected.database === database.name &&
                  selected.collection === collection
                return (
                  <li key={collection}>
                    <button
                      type="button"
                      onClick={() => onSelect({ kind: 'mongo', database: database.name, collection })}
                      className={active ? 'text-[#f5f5f5]' : 'text-[#888]'}
                    >
                      {collection}
                    </button>
                  </li>
                )
              })}
            </ul>
          </li>
        ))}
      </ul>
    )
  }

  return (
    <ul className="font-mono text-sm">
      {(metadata.schemas ?? []).map((schema) => (
        <li key={schema.name} className="mb-3">
          <div className="text-[#888]">{schema.name}</div>
          <ul className="mt-1 pl-3">
            {schema.tables.map((table) => {
              const active =
                selected?.kind === 'sql' && selected.schema === schema.name && selected.table === table.name
              return (
                <li key={table.name}>
                  <button
                    type="button"
                    onClick={() =>
                      onSelect({
                        kind: 'sql',
                        schema: schema.name,
                        table: table.name,
                        primaryKey: table.primaryKey ?? [],
                      })
                    }
                    className={active ? 'text-[#f5f5f5]' : 'text-[#888]'}
                  >
                    {table.name}
                    {table.type === 'view' ? ' · view' : ''}
                  </button>
                </li>
              )
            })}
          </ul>
        </li>
      ))}
    </ul>
  )
}
