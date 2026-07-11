# Security Policy

## Supported versions

Development builds only until 1.x wire replacement is declared stable.

## Reporting a vulnerability

If you find a security issue (especially around USB accessory handling, TLS, or local network Link ports):

1. Prefer a private report to the maintainer (do not open a public issue with exploit details).
2. Include: device model, Android version, Amni-Drive version, steps to reproduce.
3. Allow reasonable time for a fix before public disclosure.

## Threat notes for contributors

- Amni Link ports `17321`/`17322` are LAN-only by design; do not expose them to the public internet.
- Amni Wire / AOA work is experimental; treat all car sessions as untrusted until authentication is designed.
- Never commit private keys, Google service account JSON, or OEM signing material.
