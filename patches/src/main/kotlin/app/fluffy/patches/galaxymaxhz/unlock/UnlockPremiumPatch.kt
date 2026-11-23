package app.fluffy.patches.galaxymaxhz.unlock

import app.fluffy.patches.galaxymaxhz.ApkHashFeederFingerprint
import app.fluffy.patches.galaxymaxhz.LicenseInfoBuilderFingerprint
import app.fluffy.patches.galaxymaxhz.LicenseRefreshFingerprint
import app.fluffy.patches.galaxymaxhz.LoadingGateFingerprint
import app.fluffy.patches.galaxymaxhz.ProfileConfigFetcherFingerprint
import app.fluffy.patches.galaxymaxhz.RemoteProfilesCallFingerprint
import app.fluffy.patches.galaxymaxhz.SignedRequestLauncherFingerprint
import app.fluffy.patches.galaxymaxhz.VerdictGFingerprint
import app.fluffy.patches.galaxymaxhz.VerdictHFingerprint
import app.fluffy.patches.shared.Constants.COMPATIBILITY_GALAXYMAXHZ
import app.fluffy.patches.waypoint.clearBody
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// Mirrors what the licence server returns for a valid premium licence. The
// update-check keys are required by the parsers (updateId is a divisor on the
// About screen); reporting the pinned version keeps the check at "up to date".
private const val OFFLINE_LICENSE_JSON =
    "{\"0x0\":0,\"0x11\":0,\"0x9\":\"01/01/2099 00:00:00\",\"0x10\":3149313," +
        "\"0x4\":\"GPA-LOCAL-PREMIUM\"," +
        "\"0x24\":\"https://forum.xda-developers.com/conversations/add?to=tribalfs\"," +
        "\"0x12\":\"https://api.tribalfs.com/gmh/buy\"," +
        "\"0x36\":\"https://github.com/tribalfs/GalaxyMaxHzPub/wiki/Frequently-Asked-Questions-(FAQs)\"," +
        "\"0x8\":true,\"0x6\":true,\"0x7\":\"Premium\"," +
        "\"latestVersion\":\"9.2.0x\",\"latestVersionCode\":369," +
        "\"url\":\"\",\"releaseNotes\":[],\"updateId\":1}"

private fun isInvokeOf(
    instruction: Instruction?,
    definingClass: String,
    name: String,
): Boolean {
    if (instruction !is ReferenceInstruction) return false
    val reference = instruction.reference as? MethodReference ?: return false
    return reference.definingClass == definingClass && reference.name == name
}

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks premium locally and fully offline. Every signed licence " +
        "and config request is answered with a valid premium response, the startup " +
        "APK self-check that would keep the app loading forever is bypassed, and " +
        "refresh-rate profiles come from the app's own bundled device table. The " +
        "licence server never validates a re-signed install, so no server connection " +
        "is needed or attempted.",
) {
    compatibleWith(COMPATIBILITY_GALAXYMAXHZ)

    execute {
        // Feed the native tamper init its expected APK hash.
        ApkHashFeederFingerprint.method.apply {
            clearBody()
            addInstructions(
                0,
                """
                sput-wide p1, Lcom/tribalfs/gmh/application/GmhApp;->x:J
                const-wide v0, 0xd4e1f21eL
                return-wide v0
                """.trimIndent(),
            )
        }

        // Build the licence-info object directly with the premium licence type
        // (xf/b.f * xf/b.h, computed at runtime) and no expiry.
        LicenseInfoBuilderFingerprint.method.apply {
            clearBody()
            addInstructions(
                0,
                """
                sget v1, Lxf/b;->f:I
                sget v2, Lxf/b;->h:I
                mul-int/2addr v1, v2
                invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                move-result-object v1
                move-object/from16 v2, p5
                const-wide/16 v3, 0x0
                invoke-static {v3, v4}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;
                move-result-object v3
                const/4 v5, 0x1
                new-instance v6, Landroidx/appcompat/util/SeslUtil;
                invoke-direct {v6, v1, v2, v3, v5}, Landroidx/appcompat/util/SeslUtil;-><init>(Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/Long;Z)V
                return-object v6
                """.trimIndent(),
            )
        }

        VerdictGFingerprint.method.apply {
            clearBody()
            addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }
        VerdictHFingerprint.method.apply {
            clearBody()
            addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }

        // The licence-info constructor stores its boolean flag straight into
        // the field; force that flag to premium before the store.
        mutableClassDefBy("Landroidx/appcompat/util/SeslUtil;")
            .methods.first {
                it.name == "<init>" &&
                    it.parameterTypes == listOf(
                        "Ljava/lang/Integer;",
                        "Ljava/lang/String;",
                        "Ljava/lang/Long;",
                        "Z",
                    )
            }.apply { addInstructions(0, "const/4 p4, 0x1") }

        // Read the bundled zV.json resource instead of the remote profile
        // config, so the app picks its own per-device entry.
        RemoteProfilesCallFingerprint.method.apply {
            val callIndex = instructions.indexOfFirst { isInvokeOf(it, "Lt0/c1;", "z") }
            check(callIndex >= 0) { "c1.z call not found in the remote profile fetch" }
            check(instructions.getOrNull(callIndex + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT) {
                "c1.z call is not followed by move-result-object"
            }
            removeInstructions(callIndex, 2)
            addInstructions(
                callIndex,
                """
                invoke-virtual {p1}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
                move-result-object v7
                const v11, 0x7f130005
                invoke-virtual {v7, v11}, Landroid/content/res/Resources;->openRawResource(I)Ljava/io/InputStream;
                move-result-object v7
                new-instance v11, Ljava/util/Scanner;
                invoke-direct {v11, v7}, Ljava/util/Scanner;-><init>(Ljava/io/InputStream;)V
                const-string v7, "\\A"
                invoke-virtual {v11, v7}, Ljava/util/Scanner;->useDelimiter(Ljava/lang/String;)Ljava/util/Scanner;
                move-result-object v11
                invoke-virtual {v11}, Ljava/util/Scanner;->next()Ljava/lang/String;
                move-result-object v7
                """.trimIndent(),
            )
        }

        // Answer every signed request synchronously with the offline premium
        // response; the callers' own parsers store and apply it.
        SignedRequestLauncherFingerprint.method.apply {
            clearBody()
            addInstructions(
                0,
                """
                const-string v0, "$OFFLINE_LICENSE_JSON"
                new-instance v1, Lorg/json/JSONObject;
                invoke-direct {v1, v0}, Lorg/json/JSONObject;-><init>(Ljava/lang/String;)V
                new-instance v2, Lka/c;
                invoke-direct {v2, v1}, Lka/c;-><init>(Lorg/json/JSONObject;)V
                return-object v2
                """.trimIndent(),
            )
        }

        // The one caller that must not see that response (it expects the
        // server-tuned profile array) fails cleanly onto the bundled config.
        ProfileConfigFetcherFingerprint.method.apply {
            val callIndex = instructions.indexOfFirst { isInvokeOf(it, "Lka/e;", "b") }
            check(callIndex >= 0) { "signed-request call not found in the profile-config fetcher" }
            check(instructions.getOrNull(callIndex + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT) {
                "signed-request call is not followed by move-result-object"
            }
            removeInstructions(callIndex, 2)
            addInstructions(
                callIndex,
                """
                new-instance p1, Lka/a;
                const-string v5, "offline"
                invoke-direct {p1, v5}, Lka/a;-><init>(Ljava/lang/String;)V
                """.trimIndent(),
            )
        }

        // The licence refresh awaits a pending-request flow that nothing feeds
        // without the donate-app activation; send the request with no
        // parameters instead of parking forever.
        LicenseRefreshFingerprint.method.apply {
            val impl = implementation!!
            var branchIndex = -1
            for (i in 1 until impl.instructions.size) {
                val previous = impl.instructions[i - 1]
                if (previous is Instruction22c &&
                    previous.reference is FieldReference &&
                    (previous.reference as FieldReference).name == "r" &&
                    impl.instructions[i].opcode == Opcode.IF_NEZ
                ) {
                    branchIndex = i
                    break
                }
            }
            check(branchIndex >= 0) { "pending-request await branch not found in the licence refresh" }
            val target = (impl.instructions[branchIndex] as BuilderInstruction21t).target
            replaceInstruction(branchIndex, BuilderInstruction10t(Opcode.GOTO, target))
        }

        // The loading-state clear compares the stored hash against the runtime
        // Adler-32 of the installed dex; a patched dex can never match.
        LoadingGateFingerprint.method.apply {
            val impl = implementation!!
            var branchIndex = -1
            for (i in 1 until impl.instructions.size) {
                if (impl.instructions[i - 1].opcode == Opcode.CMP_LONG &&
                    impl.instructions[i].opcode == Opcode.IF_EQZ
                ) {
                    branchIndex = i
                    break
                }
            }
            check(branchIndex >= 0) { "hash comparison branch not found in the loading gate" }
            val target = (impl.instructions[branchIndex] as BuilderInstruction21t).target
            replaceInstruction(branchIndex, BuilderInstruction10t(Opcode.GOTO, target))
        }
    }
}
