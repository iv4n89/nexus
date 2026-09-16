'use client'

export function QueryEditor({
  value,
  onChange,
  onExecute,
  disabled,
}: {
  value: string
  onChange: (value: string) => void
  onExecute: () => void
  disabled: boolean
}) {
  return (
    <div className="flex flex-col gap-3">
      <textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        spellCheck={false}
        className="min-h-40 border border-[#2a2a2a] bg-black p-3 font-mono text-sm text-[#f5f5f5]"
      />
      <button
        type="button"
        disabled={disabled}
        onClick={onExecute}
        className={
          disabled
            ? 'cursor-not-allowed self-start border border-[#2a2a2a] px-4 py-2 text-sm text-[#888]'
            : 'self-start border border-[#f5f5f5] px-4 py-2 text-sm text-[#f5f5f5]'
        }
      >
        EXECUTE
      </button>
    </div>
  )
}
