#!/usr/bin/env bash
# Mints an HS256 JWT signed with the local dev key used by docker-compose.
# Usage: scripts/dev-token.sh [scopes] [subject] [ttl-seconds]
#   TOKEN=$(scripts/dev-token.sh "ledger.read ledger.write")
set -euo pipefail

scope="${1:-ledger.read ledger.write}"
subject="${2:-demo-client}"
ttl="${3:-3600}"
secret="${LEDGERFLOW_DEV_JWT_SECRET:-local-dev-only-signing-key-change-me-0123456789}"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

now=$(date +%s)
header=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
payload=$(printf '{"sub":"%s","scope":"%s","iss":"ledgerflow-dev","iat":%d,"exp":%d}' \
  "$subject" "$scope" "$now" "$((now + ttl))" | b64url)
signature=$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -hmac "$secret" -binary | b64url)

printf '%s.%s.%s\n' "$header" "$payload" "$signature"
