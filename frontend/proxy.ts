import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

export function proxy(request: NextRequest) {
  const session = request.cookies.get('JSESSIONID')
  if (!session) {
    const login = new URL('/login', request.url)
    return NextResponse.redirect(login)
  }
  return NextResponse.next()
}

export const config = {
  matcher: [
    /*
     * Exclude login, API, and Next internals/static.
     * API stays unauthenticated so CSRF/login can reach the backend
     * and unauthenticated calls return 401 JSON instead of HTML.
     */
    '/((?!login|api|_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
}
