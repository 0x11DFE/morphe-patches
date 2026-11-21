package app.fluffy.patches.scene.fixes

import app.fluffy.patches.shared.Constants.COMPATIBILITY_SCENE
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.patch.listApkEntries
import java.io.File
import java.security.MessageDigest

/**
 * The root scene-daemon gates its "adb-monitor-*" ops (the live stats behind
 * the dashboard cards and monitoring overlays) behind a local license check:
 * ~40s after the app connects, a validator flips RESTRICT=1 (root environment
 * detected via a /data/scene-permission-test probe) and monitor ops start
 * answering empty. A MASTER=1 byte overrides every tier; both bytes sit in the
 * daemon's writable data/bss pages.
 *
 * The patch extends the daemon launcher script (raw resource "up", whose
 * obfuscated archive path is discovered at patch time) with a poker that runs as root on every
 * daemon start: it locates the daemon's AES IV string ("scene.omarea.com") in
 * the process's data segment at runtime, derives the two gate bytes relative
 * to it, validates and holds them open (MASTER=1, RESTRICT=0). A background
 * loop re-pokes every 10s for as long as the daemon lives, so periodic
 * license revalidation cannot close the gate. The IV offset is cached per daemon
 * pid and scans read whole blocks, so a steady-state poke costs a handful of
 * syscalls instead of megabytes of byte-at-a-time reads. A daemon update that shifts the layout fails safe: implausible
 * byte values abort, and a failed write is restored.
 *
 * Shell text below uses a section sign (\u00A7) in place of every dollar sign,
 * because Kotlin string templates would otherwise interpolate the shell
 * variables; execute() swaps them back. Line endings are adopted from the
 * file being patched (the 2026.11 resources are CRLF).
 */
@Suppress("unused")
val daemonGateBypassPatch = patch(
    name = "Daemon gate bypass",
    description = "Keeps the scene-daemon monitor gate open so live stats (dashboard cards, " +
        "overlays) keep updating instead of freezing ~40s after connect. The daemon " +
        "launcher script re-asserts the gate on every start and fails safe on unknown " +
        "daemon layouts. Requires root.",) {
    compatibleWith(COMPATIBILITY_SCENE)

    execute {
        val daemonMd5 = findDaemonFingerprint()
        if (daemonMd5 in STALE_DAEMON_MD5) {
            println(
                "Scene daemon gate bypass: daemon build $daemonMd5 has stale gate offsets - " +
                    "poker skipped (2026.11 PR5's daemon no longer flips the IV-relative bytes)",
            )
            return@execute
        }
        val up = findLauncherResource()
        val text = up.readText()

        // Adopt the file's line endings so the anchor matches byte-exactly;
        // the resource is CRLF in the 2026 builds.
        val nl = if (text.contains("\r\n")) "\r\n" else "\n"

        // Root-branch daemon-launch tail, verified on 2026.10 Alpha13 and
        // 2026.11 PR1/PR2. A rebuild that changes it fails loudly here
        // instead of shipping a broken script.
        val reniceLine = "renice -n -20 \u00A7(pidof scene-daemon)".replace('\u00A7', '$')
        val echoLine = "    echo 'Scene-Daemon OK!'"
        val anchor = reniceLine + nl + echoLine
        if (!text.contains(anchor)) {
            throw PatchException(
                "daemon launch anchor not found in the launcher - the script " +
                    "layout changed, re-derive the insertion point",
            )
        }
        if (text.contains("morphe-patches daemon gate poker")) {
            throw PatchException("launcher already contains the gate poker - patched twice?")
        }

        // Insert between the renice (daemon up, priority set) and the OK echo.
        val block = POKER_BLOCK.trimIndent()
            .replace('\u00A7', '$')
            .replace("\n", nl)
        up.writeText(text.replace(anchor, reniceLine + nl + block + nl + echoLine))
        println("Scene daemon gate bypass: poker appended to the daemon launcher")
    }
}

/**
 * Inserted into the launcher right after the daemon starts and gets its
 * priority set. Plain shell, no indirection: find the daemon, find its AES IV
 * landmark, derive and hold the gate bytes.
 *
 * Shell constraints baked in (verified on device):
 * - /system/bin/pidof and /system/bin/dd only: busybox pidof does not see the
 *   daemon, and busybox dd lacks conv=notrunc / iflag.
 * - hex math via awk with a manual digit loop and %.0f: shell arithmetic is
 *   32-bit and awk cannot parse 0x literals.
 * - single-byte reads use iflag=skip_bytes,count_bytes with bs=1; region
 *   scans read bs=65536 blocks instead (byte-identical to bs=1, verified on
 *   device, ~100x fewer syscalls).
 * - the IV offset is cached in /dev/.mp_gate_cache keyed by daemon pid; a cache
 *   hit validates the anchor with one 16-byte read before poking.
 * - temp files under /dev: the launcher can run before /data is writable.
 */


private const val POKER_BLOCK = """
# >>> morphe-patches daemon gate poker >>>
# Hold the daemon's monitor gate open: MASTER=1 overrides every gate tier,
# RESTRICT=0 lifts the permission map. Both bytes are located relative to
# the daemon's AES IV string and validated before every write. The IV
# offset is cached per daemon pid; a region scan (block reads, not
# byte-at-a-time) runs only when the cache misses.
mp_gate_scan() {
    # List the daemon's small writable mappings (4KB < size <= 256KB).
    grep -a ' rw-p ' /proc/§MP_P/maps 2>/dev/null | while read -r LN; do
        R="§(echo "§LN" | cut -d' ' -f1)"
        S="§{R%%-*}"; E="§{R##*-}"
        SZ="§((0x§E - 0x§S))"
        if [ "§SZ" -gt 4096 ] && [ "§SZ" -le 262144 ]; then echo "§S §SZ"; fi
    done > /dev/.mp_gate_rw.§§
    MP_IV=""
    while read -r RS RZ; do
        [ -z "§RS" ] && continue
        # Hex -> decimal: awk cannot parse 0x literals and shell math is 32-bit.
        SDS=§(awk "BEGIN{b=\"§RS\";v=0;for(i=1;i<=length(b);i++){c=substr(b,i,1);d=index(\"0123456789abcdef\",c)-1;if(d<0)d=index(\"0123456789ABCDEF\",c)-1;v=v*16+d};printf \"%.0f\", v}")
        /system/bin/dd if=/proc/§MP_P/mem of=/dev/.mp_gate_buf.§§ bs=65536 iflag=skip_bytes,count_bytes skip=§SDS count=§RZ 2>/dev/null
        HIT="§(grep -a -b -o -m1 'scene.omarea.com' /dev/.mp_gate_buf.§§ | head -1 | cut -d: -f1)"
        rm -f /dev/.mp_gate_buf.§§
        if [ -n "§HIT" ]; then MP_IV="§(awk "BEGIN{printf \"%.0f\", §SDS+§HIT}")"; break; fi
    done < /dev/.mp_gate_rw.§§
    rm -f /dev/.mp_gate_rw.§§
}
mp_gate_poke() {
    MP_P="§(/system/bin/pidof scene-daemon 2>/dev/null | tr -d '\r\n')"
    [ -z "§MP_P" ] && return 0
    MP_IV=""
    if [ -s /dev/.mp_gate_cache ]; then
        if [ "§(cut -d' ' -f1 /dev/.mp_gate_cache 2>/dev/null)" = "§MP_P" ]; then
            MP_IV="§(cut -d' ' -f2 /dev/.mp_gate_cache 2>/dev/null)"
            /system/bin/dd if=/proc/§MP_P/mem of=/dev/.mp_gate_iv.§§ bs=16 iflag=skip_bytes,count_bytes skip=§MP_IV count=16 2>/dev/null
            grep -a -q 'scene.omarea.com' /dev/.mp_gate_iv.§§ || MP_IV=""
            rm -f /dev/.mp_gate_iv.§§
        fi
    fi
    [ -z "§MP_IV" ] && mp_gate_scan
    [ -z "§MP_IV" ] && return 0
    echo "§MP_P §MP_IV" > /dev/.mp_gate_cache
    # Gate bytes relative to the IV: MASTER = IV-720, RESTRICT = IV+319578
    # (deltas verified on daemon build 2026.11.17 00:01).
    MA="§(awk "BEGIN{printf \"%.0f\", §MP_IV-720}")"
    RA="§(awk "BEGIN{printf \"%.0f\", §MP_IV+319578}")"
    MB="§(/system/bin/dd if=/proc/§MP_P/mem bs=1 iflag=skip_bytes,count_bytes skip=§MA count=1 2>/dev/null | od -An -tx1 | tr -d ' \n')"
    RB="§(/system/bin/dd if=/proc/§MP_P/mem bs=1 iflag=skip_bytes,count_bytes skip=§RA count=1 2>/dev/null | od -An -tx1 | tr -d ' \n')"
    # Only touch bytes that look like the gate flags; anything else means
    # the daemon layout moved - leave it alone.
    case "§MB§RB" in
        0000|0001|0100|0101) ;;
        *) return 0 ;;
    esac
    printf '\x01' | /system/bin/dd of=/proc/§MP_P/mem bs=1 seek=§MA count=1 conv=notrunc 2>/dev/null
    printf '\x00' | /system/bin/dd of=/proc/§MP_P/mem bs=1 seek=§RA count=1 conv=notrunc 2>/dev/null
    VB="§(/system/bin/dd if=/proc/§MP_P/mem bs=1 iflag=skip_bytes,count_bytes skip=§MA count=1 2>/dev/null | od -An -tx1 | tr -d ' \n')"
    if [ "§VB" != "01" ]; then
        # Read-back failed: restore both bytes and leave the daemon stock.
        printf "\\x§MB" | /system/bin/dd of=/proc/§MP_P/mem bs=1 seek=§MA count=1 conv=notrunc 2>/dev/null
        printf "\\x§RB" | /system/bin/dd of=/proc/§MP_P/mem bs=1 seek=§RA count=1 conv=notrunc 2>/dev/null
        return 0
    fi
    if [ "§(cat /dev/.mp_gate_last 2>/dev/null)" != "§MP_P" ]; then
        echo "§(date '+%m-%d %H:%M:%S') daemon §MP_P gate open" >> "§(dirname "§0")/gp.log"
        echo "§MP_P" > /dev/.mp_gate_last
    fi
}
mp_gate_poke
# The daemon revalidates its license periodically and can clear the gate
# flags while it lives - an app update reconnects to the surviving daemon
# without re-running this launcher. So keep re-poking every 10s for as long
# as the daemon exists (idempotent when launchers overlap) and exit after
# ~5 minutes with no daemon.
( i=0
  while : ; do
    sleep 10
    if [ -n "§(/system/bin/pidof scene-daemon 2>/dev/null)" ]; then
      mp_gate_poke
      i=0
    else
      i="§((i+1))"
      [ "§i" -gt 30 ] && break
    fi
  done ) &
# <<< morphe-patches daemon gate poker <<<
"""


/**
 * Daemon builds whose data layout no longer matches the poker's IV-relative
 * offsets. 2026.11 PR5 ships a rewritten daemon (md5 a8bc3727..., IV anchor
 * moved ~75KB) that was verified on device to not flip any byte at the old
 * MASTER/RESTRICT deltas during a full app reconnect + validator cycle, while
 * dashboard stats kept updating without any poker: the monitor gate as such
 * is gone there. Poking the old offsets would hit unrelated Go globals, so
 * the poker is skipped for these builds. Verify a new daemon on device before
 * assuming the poker still applies.
 */
private val STALE_DAEMON_MD5 = setOf(
    "a8bc3727a354398d369d6e223a2c10dc", // bundled by N1 2026.11 PreRelease 5
)

/**
 * The daemon ships as a raw, extensionless res/ entry (~1.9MB ELF; the bpf
 * objects are far smaller). Find it by shape and return its MD5 hex, or an
 * empty string when the APK has no such entry.
 */
private fun findDaemonFingerprint(): String {
    val candidates = listApkEntries("res/").filter { entry ->
        !entry.substringAfterLast('/').contains('.')
    }
    for (entry in candidates) {
        val file = runCatching { resourceFile(entry) }.getOrNull() ?: continue
        if (!file.exists() || file.length() < 500_000 || file.length() > 8_000_000) continue
        file.inputStream().use { input ->
            val head = ByteArray(4)
            if (input.read(head) != 4 || head[0] != 0x7f.toByte() || head[1] != 0x45.toByte()) continue
            val md = MessageDigest.getInstance("MD5")
            md.update(head)
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
    return ""
}

/**
 * The daemon launcher is the raw resource "up", but its obfuscated archive
 * path (res/Bp in every 2026 build so far) is assigned by the resource
 * shrinker and can change on a rebuild. Discover it instead of hardcoding:
 * try the known entry first, then scan the extensionless res/ entries - the
 * obfuscator keeps extensions for known types, so extensionless is exactly
 * the raw-script set - for the daemon-launch tail.
 */
private fun findLauncherResource(): File {
    val probe = "pidof scene-daemon"

    runCatching { resourceFile("res/Bp") }.getOrNull()
        ?.takeIf { it.exists() && it.length() < 64_000 }
        ?.takeIf { it.readText().contains(probe) }
        ?.let { return it }

    val candidates = listApkEntries("res/").filter { entry ->
        !entry.substringAfterLast('/').contains('.')
    }
    for (entry in candidates) {
        val file = runCatching { resourceFile(entry) }.getOrNull() ?: continue
        if (!file.exists() || file.length() > 64_000) continue
        val content = runCatching { file.readText() }.getOrNull() ?: continue
        if (content.contains(probe) && content.contains("Scene-Daemon OK!")) {
            println("Scene daemon gate bypass: launcher found at " + entry)
            return file
        }
    }
    throw PatchException(
        "daemon launcher (raw resource 'up') not found - no extensionless res/ entry contains the launch script",
    )
}
