// Login with Keycloak using OpenID Connect: "Authorization Code flow + PKCE".
//
//  1. startLogin(): make 3 random values (verifier, state, nonce), keep them in sessionStorage,
//     and send the browser to Keycloak's login page with a HASH of the verifier (the "code challenge").
//  2. The user types their password on KEYCLOAK's page (never on ours). Keycloak sends the browser
//     back to http://localhost:5173/?code=...&state=...
//  3. finishLogin(): check "state" matches (stops someone else's login being slipped in), then swap the
//     one-time "code" for tokens at Keycloak's token endpoint, sending the original verifier.
//     Keycloak hashes the verifier and checks it matches the challenge from step 1, so a stolen code
//     is useless without the verifier (that is what PKCE is for: a browser app cannot keep a client secret).
//  4. Check "nonce" inside the ID token matches ours (stops an old ID token being replayed).
//  5. The access token is then sent to our API on every call. The API checks its signature,
//     issuer, audience and expiry (KeycloakConfig.java).

import type { AuthConfig } from './types'

type KeycloakConfig = Extract<AuthConfig, { mode: 'keycloak' }>

interface Discovery {
  issuer: string
  authorization_endpoint: string
  token_endpoint: string
  end_session_endpoint: string
}

export interface Tokens {
  accessToken: string
  idToken: string
  expiresAt: string
}

const PENDING_KEY = 'caresummary.oidc.pending'

const redirectUri = () => `${window.location.origin}/`

function base64url(bytes: Uint8Array): string {
  let s = ''
  bytes.forEach((b) => (s += String.fromCharCode(b)))
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** Cryptographically random, URL-safe text (not Math.random, which can be guessed). */
function randomString(byteCount = 32): string {
  return base64url(crypto.getRandomValues(new Uint8Array(byteCount)))
}

async function sha256(text: string): Promise<Uint8Array> {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text)))
}

/** Reads the middle part of a JWT. Only for the nonce check; the API does the real signature check. */
function readPayload(jwt: string): Record<string, unknown> {
  const part = jwt.split('.')[1] ?? ''
  const json = atob(part.replace(/-/g, '+').replace(/_/g, '/'))
  return JSON.parse(json) as Record<string, unknown>
}

// OpenID Connect "discovery": Keycloak lists its login, token and logout addresses at a standard URL,
// so we don't hard-code them.
let discovery: Promise<Discovery> | null = null
function discover(issuer: string): Promise<Discovery> {
  discovery ??= fetch(`${issuer}/.well-known/openid-configuration`)
    .then((res) => {
      if (!res.ok) throw new Error('Cannot reach the login server (Keycloak). Is it running?')
      return res.json() as Promise<Discovery>
    })
    .then((d) => {
      if (d.issuer !== issuer) throw new Error('Login server address does not match the API settings.')
      return d
    })
    .catch((e: unknown) => {
      discovery = null // try again next time
      throw e instanceof TypeError ? new Error('Cannot reach the login server (Keycloak). Is it running?') : e
    })
  return discovery
}

/** Step 1: go to Keycloak's login page. */
export async function startLogin(cfg: KeycloakConfig): Promise<void> {
  const d = await discover(cfg.issuer)
  const pending = { verifier: randomString(32), state: randomString(16), nonce: randomString(16) }
  try {
    sessionStorage.setItem(PENDING_KEY, JSON.stringify(pending))
  } catch {
    throw new Error('This browser blocks session storage, which login needs.')
  }
  const challenge = base64url(await sha256(pending.verifier))
  const url = new URL(d.authorization_endpoint)
  url.search = new URLSearchParams({
    client_id: cfg.clientId,
    response_type: 'code',
    scope: 'openid',
    redirect_uri: redirectUri(),
    state: pending.state,
    nonce: pending.nonce,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  }).toString()
  window.location.assign(url.toString())
}

/** True when Keycloak has just sent the browser back to us (?code=... or ?error=...). */
export function isLoginCallback(): boolean {
  const p = new URLSearchParams(window.location.search)
  return p.has('state') && (p.has('code') || p.has('error'))
}

// React (StrictMode, dev) runs effects twice. A code can be used only once, so remember the first try.
let finishing: Promise<Tokens> | null = null

/** Steps 3-4: swap the code for tokens. */
export function finishLogin(cfg: KeycloakConfig): Promise<Tokens> {
  finishing ??= exchangeCode(cfg)
  return finishing
}

async function exchangeCode(cfg: KeycloakConfig): Promise<Tokens> {
  const params = new URLSearchParams(window.location.search)
  window.history.replaceState(null, '', window.location.pathname) // remove the code from the address bar

  let pending: { verifier: string; state: string; nonce: string } | null = null
  try {
    pending = JSON.parse(sessionStorage.getItem(PENDING_KEY) ?? 'null')
    sessionStorage.removeItem(PENDING_KEY) // one use only
  } catch {
    /* storage blocked */
  }

  const error = params.get('error')
  if (error) throw new Error(params.get('error_description') ?? `Login failed: ${error}`)
  if (!pending || params.get('state') !== pending.state) {
    throw new Error('Login check failed (state did not match). Please log in again.')
  }

  const d = await discover(cfg.issuer)
  const res = await fetch(d.token_endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code: params.get('code') ?? '',
      redirect_uri: redirectUri(),
      client_id: cfg.clientId,
      code_verifier: pending.verifier,
    }),
  })
  if (!res.ok) throw new Error('Login failed: the login server did not accept the code. Please try again.')
  const t = (await res.json()) as { access_token: string; id_token: string; expires_in: number }

  if (readPayload(t.id_token).nonce !== pending.nonce) {
    throw new Error('Login check failed (nonce did not match). Please log in again.')
  }
  return {
    accessToken: t.access_token,
    idToken: t.id_token,
    expiresAt: new Date(Date.now() + t.expires_in * 1000).toISOString(),
  }
}

/** Log out of Keycloak too (ends the single sign-on session), then come back to our app. */
export async function logoutOfKeycloak(cfg: KeycloakConfig, idToken: string | undefined): Promise<void> {
  const d = await discover(cfg.issuer)
  const url = new URL(d.end_session_endpoint)
  const p = new URLSearchParams({ client_id: cfg.clientId, post_logout_redirect_uri: redirectUri() })
  if (idToken) p.set('id_token_hint', idToken)
  url.search = p.toString()
  window.location.assign(url.toString())
}
