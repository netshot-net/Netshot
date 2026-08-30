#!/usr/bin/env node
/**
 * Netshot Dev OIDC Identity Provider
 *
 * Minimal OIDC IdP for development and testing.
 * Implements the authorization code flow with client_secret_basic.
 * Serves a simple web page to pick from pre-defined accounts (one per role).
 * No external dependencies — uses Node.js built-in modules only.
 *
 * Usage:
 *   node tools/dev-oidc-idp.js
 *
 * Environment variables:
 *   PORT           Listening port          (default: 4000)
 *   CLIENT_ID      OIDC client ID          (default: netshot)
 *   CLIENT_SECRET  OIDC client secret      (default: secret)
 *   ISSUER         Backchannel base URL    (default: http://localhost:PORT)
 *                  Used for `iss`, the discovery doc's `issuer`, `token_endpoint`
 *                  and `jwks_uri` — i.e. the address Netshot itself uses to reach
 *                  this IdP directly (e.g. http://idp:4000 on a Compose network).
 *   PUBLIC_URL     Browser-facing base URL (default: same as ISSUER)
 *                  Used for `authorization_endpoint` and `end_session_endpoint` —
 *                  the address Netshot redirects the *user's browser* to, which
 *                  may differ from ISSUER (e.g. http://localhost:4000 or an
 *                  external IP) when Netshot reaches the IdP over a Docker
 *                  network but the browser reaches it via published ports.
 *
 * Netshot configuration (netshot.conf):
 *   netshot.aaa.oidc.idp.url=http://localhost:4000
 *   netshot.aaa.oidc.clientid=netshot
 *   netshot.aaa.oidc.clientsecret=secret
 */
'use strict';

const http   = require('http');
const crypto = require('crypto');

// ── Configuration ─────────────────────────────────────────────────────────────

const PORT          = Number(process.env.PORT)   || 4000;
const CLIENT_ID     = process.env.CLIENT_ID      || 'netshot';
const CLIENT_SECRET = process.env.CLIENT_SECRET  || 'secret';
const ISSUER        = process.env.ISSUER         || `http://localhost:${PORT}`;
const PUBLIC_URL    = process.env.PUBLIC_URL     || ISSUER;
const KEY_ID        = 'dev-key-1';

// ── Pre-defined accounts (one per Netshot role) ───────────────────────────────

const USERS = [
  { username: 'readonly-user',         role: 'read-only'          },
  { username: 'operator-user',         role: 'operator'           },
  { username: 'readwrite-user',        role: 'read-write'         },
  { username: 'execreadwrite-user',    role: 'execute-read-write' },
  { username: 'admin-user',            role: 'admin'              },
];

// ── RSA key pair — generated once at startup ──────────────────────────────────

process.stdout.write('Generating RSA key pair… ');
const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const publicJwk = publicKey.export({ format: 'jwk' });
console.log('done.');

// ── In-memory authorization code store ───────────────────────────────────────
// code → { user, redirectUri, nonce, expiry }

const codes = new Map();

// Purge expired codes every minute
setInterval(() => {
  const now = Date.now();
  for (const [code, entry] of codes) {
    if (entry.expiry < now) codes.delete(code);
  }
}, 60_000).unref();

// ── Utilities ─────────────────────────────────────────────────────────────────

function base64url(value) {
  const json = typeof value === 'string' ? value : JSON.stringify(value);
  return Buffer.from(json).toString('base64url');
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function isLocalhostUri(uri) {
  try {
    const { hostname } = new URL(uri);
    return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]';
  } catch {
    return false;
  }
}

function readBody(req) {
  return new Promise((resolve) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
  });
}

function parseForm(raw) {
  const result = {};
  for (const [k, v] of new URLSearchParams(raw)) result[k] = v;
  return result;
}

function sendJson(res, status, body) {
  res.writeHead(status, {
    'Content-Type':  'application/json',
    'Cache-Control': 'no-store',
    'Pragma':        'no-cache',
  });
  res.end(JSON.stringify(body));
}

function sendError(res, status, message) {
  res.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end(message);
}

// ── JWT (RS256) ───────────────────────────────────────────────────────────────

function createIdToken(user, nonce) {
  const now     = Math.floor(Date.now() / 1000);
  const header  = { alg: 'RS256', typ: 'JWT', kid: KEY_ID };
  const payload = {
    iss:                ISSUER,
    sub:                user.username,
    aud:                CLIENT_ID,
    iat:                now,
    exp:                now + 3600,
    preferred_username: user.username,
    role:               user.role,
  };
  if (nonce) payload.nonce = nonce;

  const signingInput = `${base64url(header)}.${base64url(payload)}`;
  const sig = crypto.createSign('SHA256').update(signingInput).sign(privateKey, 'base64url');
  return `${signingInput}.${sig}`;
}

// ── HTML ──────────────────────────────────────────────────────────────────────

function renderSelectPage(queryObj) {
  // Re-emit all original OIDC params as hidden inputs so they survive the form POST
  const hiddenInputs = Object.entries(queryObj)
    .map(([k, v]) => `<input type="hidden" name="${escapeHtml(k)}" value="${escapeHtml(v)}">`)
    .join('\n        ');

  const accountButtons = USERS.map((u) => `
      <form method="GET" action="/authorize/confirm">
        ${hiddenInputs}
        <input type="hidden" name="_user" value="${escapeHtml(u.username)}">
        <button type="submit">
          <span class="name">${escapeHtml(u.username)}</span>
          <span class="badge">${escapeHtml(u.role)}</span>
        </button>
      </form>`).join('');

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Netshot Dev OIDC — Select Account</title>
  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: system-ui, -apple-system, sans-serif;
      background: #f0f2f5;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
    }
    .card {
      background: #fff;
      border-radius: 10px;
      box-shadow: 0 4px 20px rgba(0,0,0,.1);
      padding: 2rem 2rem 1.5rem;
      width: 100%;
      max-width: 420px;
    }
    .logo {
      font-size: .7rem;
      font-weight: 700;
      letter-spacing: .12em;
      text-transform: uppercase;
      color: #4a90d9;
      margin-bottom: .5rem;
    }
    h1 { font-size: 1.3rem; color: #111; margin-bottom: .3rem; }
    .subtitle { font-size: .85rem; color: #666; margin-bottom: 1.5rem; }
    .accounts { display: flex; flex-direction: column; gap: .5rem; }
    button {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      padding: .7rem 1rem;
      border: 1.5px solid #e5e7eb;
      border-radius: 7px;
      background: #fafafa;
      cursor: pointer;
      font-size: .9rem;
      text-align: left;
      transition: background .12s, border-color .12s, box-shadow .12s;
    }
    button:hover {
      background: #eef4fd;
      border-color: #4a90d9;
      box-shadow: 0 0 0 3px rgba(74,144,217,.15);
    }
    .name { font-weight: 600; color: #1a1a2e; }
    .badge {
      font-size: .73rem;
      font-weight: 600;
      color: #fff;
      background: #4a90d9;
      border-radius: 20px;
      padding: .2rem .7rem;
      white-space: nowrap;
    }
    .notice {
      margin-top: 1.5rem;
      padding-top: 1rem;
      border-top: 1px solid #f0f0f0;
      font-size: .72rem;
      color: #bbb;
      text-align: center;
    }
  </style>
</head>
<body>
  <div class="card">
    <p class="logo">Dev OIDC IdP</p>
    <h1>Select an account</h1>
    <p class="subtitle">Choose a pre-defined Netshot account to authenticate with.</p>
    <div class="accounts">
${accountButtons}
    </div>
    <p class="notice">Development only — not for production use</p>
  </div>
</body>
</html>`;
}

function renderLoggedOutPage() {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Signed out — Netshot Dev OIDC</title>
  <style>
    body { font-family: system-ui, sans-serif; text-align: center; padding: 5rem 1rem; color: #444; }
    h1 { font-size: 1.4rem; margin-bottom: .75rem; }
    a { color: #4a90d9; }
  </style>
</head>
<body>
  <h1>You have been signed out</h1>
  <p>Return to <a href="/">Netshot Dev OIDC IdP</a>.</p>
</body>
</html>`;
}

// ── Request handler ───────────────────────────────────────────────────────────

async function handler(req, res) {
  const parsed   = new URL(req.url, `http://localhost`);
  const pathname = parsed.pathname;
  const query    = Object.fromEntries(parsed.searchParams);

  // ── GET /.well-known/openid-configuration ──────────────────────────────────
  if (req.method === 'GET' && pathname === '/.well-known/openid-configuration') {
    return sendJson(res, 200, {
      issuer:                                ISSUER,
      authorization_endpoint:                `${PUBLIC_URL}/authorize`,
      token_endpoint:                        `${ISSUER}/token`,
      jwks_uri:                              `${ISSUER}/jwks.json`,
      end_session_endpoint:                  `${PUBLIC_URL}/end-session`,
      response_types_supported:              ['code'],
      subject_types_supported:               ['public'],
      id_token_signing_alg_values_supported: ['RS256'],
      scopes_supported:                      ['openid'],
      token_endpoint_auth_methods_supported: ['client_secret_basic'],
      grant_types_supported:                 ['authorization_code'],
      claims_supported:                      ['sub', 'iss', 'aud', 'iat', 'exp', 'preferred_username', 'role', 'nonce'],
    });
  }

  // ── GET /jwks.json ─────────────────────────────────────────────────────────
  if (req.method === 'GET' && pathname === '/jwks.json') {
    return sendJson(res, 200, {
      keys: [{ ...publicJwk, use: 'sig', alg: 'RS256', kid: KEY_ID }],
    });
  }

  // ── GET /authorize ─────────────────────────────────────────────────────────
  if (req.method === 'GET' && pathname === '/authorize') {
    const { client_id, redirect_uri, response_type } = query;

    if (client_id !== CLIENT_ID) {
      return sendError(res, 400, `Unknown client_id: ${client_id}`);
    }
    if (response_type !== 'code') {
      return sendError(res, 400, 'Only response_type=code is supported');
    }
    if (!redirect_uri || !isLocalhostUri(redirect_uri)) {
      return sendError(res, 400, 'redirect_uri must point to localhost');
    }

    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    return res.end(renderSelectPage(query));
  }

  // ── GET /authorize/confirm (user clicked an account button) ───────────────
  if (req.method === 'GET' && pathname === '/authorize/confirm') {
    const { _user, client_id, redirect_uri, state, nonce } = query;

    if (client_id !== CLIENT_ID) {
      return sendError(res, 400, 'Unknown client_id');
    }
    if (!redirect_uri || !isLocalhostUri(redirect_uri)) {
      return sendError(res, 400, 'Invalid redirect_uri');
    }
    const user = USERS.find((u) => u.username === _user);
    if (!user) {
      return sendError(res, 400, `Unknown user: ${_user}`);
    }

    const code = crypto.randomBytes(16).toString('hex');
    codes.set(code, { user, redirectUri: redirect_uri, nonce, expiry: Date.now() + 60_000 });

    const dest = new URL(redirect_uri);
    dest.searchParams.set('code', code);
    if (state) dest.searchParams.set('state', state);

    res.writeHead(302, { Location: dest.toString() });
    return res.end();
  }

  // ── POST /token ────────────────────────────────────────────────────────────
  if (req.method === 'POST' && pathname === '/token') {
    // Authenticate client via HTTP Basic Auth
    const authHeader = req.headers['authorization'] || '';
    const basicMatch = authHeader.match(/^Basic (.+)$/i);
    if (!basicMatch) {
      res.setHeader('WWW-Authenticate', 'Basic realm="OIDC"');
      return sendJson(res, 401, { error: 'invalid_client', error_description: 'Missing Authorization header' });
    }

    const decoded  = Buffer.from(basicMatch[1], 'base64').toString('utf8');
    const colonIdx = decoded.indexOf(':');
    const cid      = decoded.slice(0, colonIdx);
    const csecret  = decoded.slice(colonIdx + 1);
    if (cid !== CLIENT_ID || csecret !== CLIENT_SECRET) {
      return sendJson(res, 401, { error: 'invalid_client', error_description: 'Bad credentials' });
    }

    const body = parseForm(await readBody(req));

    if (body.grant_type !== 'authorization_code') {
      return sendJson(res, 400, { error: 'unsupported_grant_type' });
    }

    const entry = codes.get(body.code);
    if (!entry || Date.now() > entry.expiry) {
      codes.delete(body.code);
      return sendJson(res, 400, { error: 'invalid_grant', error_description: 'Authorization code expired or not found' });
    }
    if (entry.redirectUri !== body.redirect_uri) {
      return sendJson(res, 400, { error: 'invalid_grant', error_description: 'redirect_uri mismatch' });
    }

    codes.delete(body.code); // authorization codes are single-use

    const idToken     = createIdToken(entry.user, entry.nonce);
    const accessToken = crypto.randomBytes(16).toString('hex');

    return sendJson(res, 200, {
      access_token: accessToken,
      token_type:   'Bearer',
      expires_in:   3600,
      id_token:     idToken,
    });
  }

  // ── GET /end-session ───────────────────────────────────────────────────────
  if (pathname === '/end-session') {
    const { post_logout_redirect_uri } = query;
    if (post_logout_redirect_uri && isLocalhostUri(post_logout_redirect_uri)) {
      res.writeHead(302, { Location: post_logout_redirect_uri });
      return res.end();
    }
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    return res.end(renderLoggedOutPage());
  }

  // ── 404 ────────────────────────────────────────────────────────────────────
  sendError(res, 404, 'Not found');
}

// ── Start server ──────────────────────────────────────────────────────────────

const server = http.createServer((req, res) => {
  handler(req, res).catch((err) => {
    console.error('[error]', err);
    if (!res.headersSent) sendError(res, 500, 'Internal server error');
  });
});

server.listen(PORT, '0.0.0.0', () => {
  const line = '─'.repeat(44);
  console.log(`\n┌${line}┐`);
  console.log(`│  Netshot Dev OIDC IdP${' '.repeat(22)}│`);
  console.log(`└${line}┘\n`);
  console.log(`  Issuer     : ${ISSUER}`);
  console.log(`  Discovery  : ${ISSUER}/.well-known/openid-configuration`);
  console.log(`  Client ID  : ${CLIENT_ID}`);
  console.log(`  Secret     : ${CLIENT_SECRET}`);
  console.log('');
  console.log('  Netshot configuration:');
  console.log(`    netshot.aaa.oidc.idp.url=${ISSUER}`);
  console.log(`    netshot.aaa.oidc.clientid=${CLIENT_ID}`);
  console.log(`    netshot.aaa.oidc.clientsecret=${CLIENT_SECRET}`);
  console.log('');
  console.log('  Pre-defined accounts:');
  for (const u of USERS) {
    console.log(`    ${u.username.padEnd(24)}  →  ${u.role}`);
  }
  console.log('');
});
