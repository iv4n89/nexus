'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'

const NAV = [
  { href: '/', label: 'Dashboard', kind: 'route' as const },
  { href: '/activity', label: 'Activity', kind: 'route' as const },
  { href: '#', label: 'Settings', kind: 'placeholder' as const },
]

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()

  return (
    <div className="flex min-h-full flex-1 flex-col bg-black text-[#f5f5f5]">
      <header className="flex items-center justify-between border-b border-[#2a2a2a] px-6 py-4">
        <Link href="/" className="text-sm tracking-[0.4em]">
          NEXUS
        </Link>
        <nav className="flex items-center gap-6 text-sm">
          {NAV.map((item) => {
            if (item.kind === 'placeholder') {
              return (
                <a key={item.label} href={item.href} className="text-[#888]">
                  {item.label}
                </a>
              )
            }
            const active =
              item.href === '/' ? pathname === '/' : pathname === item.href || pathname.startsWith(`${item.href}/`)
            return (
              <Link
                key={item.label}
                href={item.href}
                className={active ? 'text-[#f5f5f5]' : 'text-[#888]'}
              >
                {item.label}
              </Link>
            )
          })}
        </nav>
      </header>
      <main className="mx-auto w-full max-w-3xl flex-1 px-6 py-10">{children}</main>
    </div>
  )
}
