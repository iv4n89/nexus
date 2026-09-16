'use client'

import { FormEvent, useState } from 'react'
import { login } from './api'

export function LoginForm() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setPending(true)
    try {
      await login(username, password)
      window.location.href = '/'
    } catch (err) {
      setError(err instanceof Error ? err.message : 'LOGIN_FAILED')
      setPending(false)
    }
  }

  return (
    <form onSubmit={onSubmit} className="flex w-full max-w-sm flex-col gap-4">
      <label className="flex flex-col gap-1 text-sm">
        Username
        <input
          name="username"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
          className="border border-white bg-black px-3 py-2 text-white outline-none"
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Password
        <input
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          className="border border-white bg-black px-3 py-2 text-white outline-none"
        />
      </label>
      {error ? (
        <p role="alert" className="text-sm">
          {error}
        </p>
      ) : null}
      <button
        type="submit"
        disabled={pending}
        className="border border-white bg-black px-3 py-2 text-white disabled:opacity-50"
      >
        {pending ? 'Signing in…' : 'Sign in'}
      </button>
    </form>
  )
}
