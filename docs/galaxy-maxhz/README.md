# Galaxy MaxHz (com.tribalfs.gmh)

Patches for Galaxy MaxHz, the Samsung refresh-rate and display control app by
tribalfs. Premium is a per-device license: the user buys the separate
"Galaxy Max Hz Donation" app (com.tribalfs.gmh_donate) or donates via PayPal,
then activates with the Google Play order number or PayPal code.

## Unlock Premium

Unlocks all premium features by forcing the license verdicts to true. The
native license library and its tamper checks are kept out of the loop, so the
patched and re-signed APK runs normally.

Verified on version 9.2.0x (versionCode 369).

## How the license works

- `Lia/m1;` is the license repository. It stores license fields in DataStore
  (`gmh_ds.preferences_pb`) under hex-string keys: "0x3" (Integer), "0x4"
  (Boolean), "0x5" (String, the order number), "0x6" (Long), "0x9" (Integer),
  "0x17" (String). `m1.a(Lo1/a;)Z` reads the snapshot and asks
  `Lt0/c1;->g1(...)` for the verdict.
- `Lt0/c1;` is the validator object. Its checks run in `libgmh-lib.so` through
  six JNI exports (`Java_t0_c1_e/f/g/h/i/z`):
  - `g1` wraps native `g`: the premium verdict. Day-math on the stored Long
    (expiry) plus comparisons against config ints in `Lxf/b;`
    (513, 769, 1025, 1281, 1537, ...).
  - `h1` wraps native `h`: a secondary verdict that feeds the worker flow.
  - `f1` wraps native `f`: builds `Landroidx/appcompat/util/SeslUtil;`
    (Integer, String, Long, boolean), the license-info value class. Despite
    the name this is an app class renamed by R8.
  - `i2` and `z` build JSON for the license request and remote config.
  - `s` (from `GmhApp.onCreate`) is the native init: it resolves method IDs
    for the callbacks `e1` (APK hash) and `k` (toast), verifies an APK-derived
    hash against the constant `0xd4e1f21e`, and contains a deliberate crash
    path for tampered builds.
- `Luf/a;->a(J)` decrypts app strings: all literals are seed-decrypted from a
  blob string in the dex. Seeds share the low word `0x012f1e0d`.
- The donation app broadcasts `com.tribalfs.gmh.action_DONATED`;
  `GmhBaseListener` forwards to `m1.d(code)`. The server is
  `api.tribalfs.com`; responses use the same "0xN" JSON keys.
- `KapWorker` revalidates the license. Its terminal step checks the premium
  flow and stops when premium is true.

## What Unlock Premium changes

| Target | Change |
| --- | --- |
| `Lt0/c1;->g1(Landroid/content/Context;Ljava/lang/Long;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;)Z` | return true |
| `Lt0/c1;->h1(Landroid/content/Context;Ljava/lang/Integer;)Z` | return true |
| `Lt0/c1;->f1(...)Landroidx/appcompat/util/SeslUtil;` | build SeslUtil from params, premium true |
| `Lt0/c1;->i2(...)Lorg/json/JSONObject;` | return empty JSONObject |
| `Lt0/c1;->z(Landroid/content/Context;I)Lorg/json/JSONObject;` | return empty JSONObject |
| `Landroidx/appcompat/util/SeslUtil;-><init>(Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/Long;Z)V` | force the boolean parameter true |
| `Lcom/tribalfs/gmh/application/GmhApp;->onCreate()V` | nop the `c1.s(Context)` call |

## Random device ID

Replaces the fixed salt in the native device fingerprint with a random salt
baked in when the patch is applied. Each patched APK reports its own stable
device id to the licensing server; re-patch the APK to get a new id. The
local license check does not use the salt and keeps working.

The salt is generated per patch run, like the Scene "Random device ID" patch.
With Unlock Premium applied the license requests are stubbed, so this patch
matters when the app runs the native request path.

Verified on version 9.2.0x (versionCode 369).

### How the device id is built

`libgmh-lib.so` builds the device id from reflection specs that
`Lt0/c1;->hh()` and `Lt0/c1;->hg()` return:

- `hh()` = ["android_id", "android/provider/Settings$Secure", "getString",
  "(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;",
  "TR2IWE3S7uNtKeHz4A7GNA=="]. The last element is the digest salt.
- `hg()` = the MessageDigest/Base64 pipeline plus the same salt.
- `hi()`/`hj()` = APK signature reads. `hk()` = boot_count.

The patch keeps elements 0 to 3 unchanged and bakes a random 24-char Base64
salt as element 4. The digest over android_id plus the random salt makes the
reported device id unique per patched APK. The local verdict `g` validates
the stored fields against the APK hash only, so an activated license stays
valid.

## Verify on device

1. Apply the patch in Morphe Manager and install the patched APK.
2. Open Galaxy MaxHz. Premium-only entries (for example per-app profiles,
   resolution control in quick settings, max Hz presets) are available.
3. The donate dialog reports premium active.

## New app version

R8 names such as `Lt0/c1;` and `Luf/a;` change between releases. Locate the
validator by the string-decrypt calls (`Luf/a;->a(J)` with seeds ending
`0x012f1e0d`) and by the native exports `Java_t0_c1_*` in libgmh-lib.so, then
re-verify each target above and add the version to the compatibility constant.
