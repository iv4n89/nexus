import { LoginForm } from '@/features/auth/login-form'

export default function LoginPage() {
  return (
    <main className="flex min-h-full flex-1 flex-col items-center justify-center gap-8 bg-black px-4 text-white">
      <h1 className="text-2xl tracking-[0.4em]">NEXUS</h1>
      <LoginForm />
    </main>
  )
}
