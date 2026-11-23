# Galaxy MaxHz (com.tribalfs.gmh)

Patches for Galaxy MaxHz, the Samsung refresh-rate and display control app by
tribalfs. Premium is a per-device license: the user buys the separate
"Galaxy Max Hz Donation" app (com.tribalfs.gmh_donate) or donates via PayPal,
then activates with the Google Play order number or PayPal code.

## Unlock Premium

Unlocks premium fully offline. Every signed server request is answered
locally with a valid premium response, the startup APK self-check is
bypassed, and profile data comes from the app's own bundled device table.
No server connection is made or needed for licensing.

## How the license works

The app signs every license request with a digest of its signing
certificate folded in by the native `t0/c1` library, so a re-signed APK
cannot talk to the real server at all. The server responses carry
hex-named keys (`0x0` status, `0x10` licence type, `0x9` expiry date,
`0x4` order code, plus `0x24`/`0x12`/`0x36` URL bookkeeping) that the app
parses and stores in its DataStore. Three layers defend the premium
state: the native init dereferences null when the installed APK hash
does not match, `x5/e.R(boolean)` keeps the loading state active when
`GmhApp.x != GmhApp.y` (a runtime Adler-32 of the installed dex), and the
native `f`/`g`/`h` exports can exit the process on invalid licence input.

## What Unlock Premium changes

- `t0/c1.e1` returns the expected APK hash so the native init completes,
  and stores it into `GmhApp.x`.
- `t0/c1.f1` builds the licence-info object directly with the premium
  licence type (`xf/b.f * xf/b.h`, computed at runtime) and no expiry;
  `g1`/`h1` answer their verdicts with true.
- The `SeslUtil` constructor forces its premium boolean before storing it.
- `fc/b.s` reads the bundled `zV.json` (raw resource `0x7f130005`) with a
  `Scanner` instead of calling the remote profile fetch, so the app picks
  the profile entry matching the device on its own.
- `ka/e.b` — the single choke point for every signed request — returns a
  synchronously parsed premium response. The callers' own parsers store
  and apply it as if the server had sent it.
- `pa/e` is the one caller that must not see that response (it expects the
  server-tuned profile array under `0x13`); its call site now constructs
  a plain offline failure so the bundled-config fallback runs.
- `yf/b` skips an await on a pending-request flow that nothing feeds
  without the donate-app activation; the licence refresh then completes.
- `x5/e.R` no longer compares the stored hash against the runtime
  Adler-32 of the installed dex, which is what kept a patched app in its
  loading state forever.

The offline response reports the pinned version as the latest one, so the
in-app update check always completes with "up to date". Watch upstream
releases yourself; when a new Galaxy MaxHz version ships, the patch is
bumped to it anyway.

## Random device ID

Replaces the device identity string the app sends with a random one,
baked per patch run so it stays stable for that install.

## Verify on device

1. Patch a stock 9.2.0x APK with Unlock Premium.
2. Install, open once, and tap through the Android permission-review
   dialog (the app targets an old SDK, so Android shows it once per
   install).
3. The splash clears, the brief loading indicator hides, and the About
   screen shows Premium.
4. Airplane mode changes nothing: every request path is answered locally.

## New app version

Re-run the fingerprints against the new APK: the R8 names behind them
(`t0/c1`, `fc/b`, `ka/e`, `pa/e`, `yf/b`, `x5/e`) move between releases.
Update `COMPATIBILITY_GALAXYMAXHZ` with the new version, refresh the
premium licence-type constant if `xf/b` changed, and keep the version
keys in `OFFLINE_LICENSE_JSON` in sync with the pinned target.
