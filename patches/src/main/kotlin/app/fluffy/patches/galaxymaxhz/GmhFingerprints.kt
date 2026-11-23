package app.fluffy.patches.galaxymaxhz

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall

/**
 * Hash feeder (t0/c1.e1 on 9.2.0x): the static (Context, long) -> long wrapper
 * that stores the caller-supplied expected hash into GmhApp.x and then hashes
 * the installed APK through yf/a. Pinned by the yf/a.a call and signature.
 */
object ApkHashFeederFingerprint : Fingerprint(
    isStatic = true,
    returnType = "J",
    parameters = listOf("Landroid/content/Context;", "J"),
    filters = listOf(
        methodCall(definingClass = "Lyf/a;", name = "a"),
    ),
)

/**
 * Licence-info builder (t0/c1.f1): wraps the native f export and returns the
 * SeslUtil licence object. The only caller of t0/c1.f.
 */
object LicenseInfoBuilderFingerprint : Fingerprint(
    returnType = "Landroidx/appcompat/util/SeslUtil;",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/Long;",
        "Ljava/lang/Integer;",
        "Ljava/lang/Integer;",
        "Ljava/lang/String;",
        "I",
    ),
    filters = listOf(
        methodCall(definingClass = "Lt0/c1;", name = "f"),
    ),
)

/** Verdict wrapper (t0/c1.g1): the only caller of the native g export. */
object VerdictGFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/Long;",
        "Ljava/lang/Integer;",
        "Ljava/lang/Integer;",
        "Ljava/lang/String;",
    ),
    filters = listOf(
        methodCall(definingClass = "Lt0/c1;", name = "g"),
    ),
)

/** Verdict wrapper (t0/c1.h1): the only caller of the native h export. */
object VerdictHFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/Integer;"),
    filters = listOf(
        methodCall(definingClass = "Lt0/c1;", name = "h"),
    ),
)

/**
 * Signed-request launcher (ka/e.b): the single choke point every licence,
 * config and update request goes through. Constructs the jf/o coroutine and
 * dispatches it with c0.F.
 */
object SignedRequestLauncherFingerprint : Fingerprint(
    isStatic = true,
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "Lka/e;",
        "I",
        "Lia/l9;",
        "Lcom/google/gson/JsonArray;",
        "Lle/c;",
        "I",
    ),
    filters = listOf(
        methodCall(definingClass = "Ljf/o;", name = "<init>"),
        methodCall(definingClass = "Lef/c0;", name = "F"),
    ),
)

/**
 * Remote profile fetch (fc/b.s): the only call site of the native z export,
 * which returns the server-tuned profile config as a JSON string.
 */
object RemoteProfilesCallFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        methodCall(definingClass = "Lt0/c1;", name = "z"),
    ),
)

/**
 * Profile-config fetcher (pa/e.q): reads the yf/h holder's executor field,
 * awaits the pending-request flow, then calls the signed-request launcher
 * with request type 6.
 */
object ProfileConfigFetcherFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(definingClass = "Lyf/h;", name = "e"),
        methodCall(definingClass = "Lhf/m;", name = "j"),
        methodCall(definingClass = "Lka/e;", name = "b"),
    ),
)

/**
 * Licence refresh (yf/b.q): reads its own pending-request field, awaits it,
 * then calls the signed-request launcher with request type 2.
 */
object LicenseRefreshFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(definingClass = "Lyf/b;", name = "r"),
        methodCall(definingClass = "Lka/e;", name = "b"),
    ),
)

/**
 * Loading gate (x5/e.R): compares the stored native hash GmhApp.x against the
 * runtime Adler-32 of the installed dex GmhApp.y and keeps the loading state
 * active when they differ.
 */
object LoadingGateFingerprint : Fingerprint(
    isStatic = true,
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        fieldAccess(definingClass = "Lcom/tribalfs/gmh/application/GmhApp;", name = "x"),
        fieldAccess(definingClass = "Lcom/tribalfs/gmh/application/GmhApp;", name = "y"),
    ),
)
