package app.fluffy.patches.galaxymaxhz.unlock

import app.fluffy.patches.shared.Constants.COMPATIBILITY_GALAXYMAXHZ
import app.fluffy.patches.waypoint.clearBody
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction22c
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val OFFLINE_LICENSE_JSON =
    "{\"0x0\":0,\"0x11\":0,\"0x9\":\"01/01/2099 00:00:00\",\"0x10\":3149313," +
        "\"0x4\":\"GPA-LOCAL-PREMIUM\"," +
        "\"0x24\":\"https://forum.xda-developers.com/conversations/add?to=tribalfs\"," +
        "\"0x12\":\"https://api.tribalfs.com/gmh/buy\"," +
        "\"0x36\":\"https://github.com/tribalfs/GalaxyMaxHzPub/wiki/Frequently-Asked-Questions-(FAQs)\"," +
        "\"0x8\":true,\"0x6\":true,\"0x7\":\"Premium\"," +
        "\"latestVersion\":\"9.2.1x\",\"latestVersionCode\":370," +
        "\"url\":\"https://github.com/tribalfs/GalaxyMaxHzPub/releases/download/v9.2/galaxy_max_hz_v9.2.apk\"," +
        "\"releaseNotes\":[],\"updateId\":1}"

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
    description = "Unlocks premium locally and fully offline: every license and config " +
        "request is answered with a valid premium response, the APK self-check that would " +
        "keep the app loading forever is bypassed, and refresh-rate profiles are served " +
        "from the app's own bundled device table. The in-app update check reports the " +
        "real latest release with its download link.",
) {
    compatibleWith(COMPATIBILITY_GALAXYMAXHZ)

    execute {
        val validator = mutableClassDefBy("Lt0/c1;")

        // e1 feeds the native tamper init; hand it the expected APK hash and store it.
        validator.methods.first { it.name == "e1" && it.returnType == "J" }.apply {
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

        // f1 builds the license-info object; report the premium licence type with no expiry.
        validator.methods.first {
            it.name == "f1" && it.returnType == "Landroidx/appcompat/util/SeslUtil;"
        }.apply {
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

        // g1/h1 wrap the native license verdicts; both are unconditionally true.
        validator.methods.first { it.name == "g1" && it.returnType == "Z" }.apply {
            clearBody()
            addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }
        validator.methods.first { it.name == "h1" && it.returnType == "Z" }.apply {
            clearBody()
            addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }

        // The license-info constructor stores its boolean flag straight into the field;
        // force that flag to premium before the store.
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

        // fc/b.s asks the native z() for the remote profile config; read the bundled
        // zV.json resource instead so the app picks its own per-device entry.
        mutableClassDefBy("Lfc/b;")
            .methods.first { it.name == "s" }
            .apply {
                val idx = instructions.indexOfFirst { isInvokeOf(it, "Lt0/c1;", "z") }
                check(idx >= 0) { "c1.z call not found in fc/b.s" }
                check(instructions.getOrNull(idx + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT) {
                    "c1.z call is not followed by move-result-object"
                }
                removeInstructions(idx, 2)
                addInstructions(
                    idx,
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

        // ka/e.b is the single choke point for every signed request. Answer every call
        // synchronously with the offline premium response; the callers' own parsers
        // store and apply it as if the server had sent it.
        mutableClassDefBy("Lka/e;")
            .methods.first {
                it.name == "b" &&
                    it.parameterTypes == listOf(
                        "Lka/e;",
                        "I",
                        "Lia/l9;",
                        "Lcom/google/gson/JsonArray;",
                        "Lle/c;",
                        "I",
                    )
            }.apply {
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

        // pa/e fetches the server-tuned profile config (request type 6). Fail that call
        // site outright so the app falls back to the bundled config read above.
        mutableClassDefBy("Lpa/e;")
            .methods.first { it.name == "q" }
            .apply {
                val idx = instructions.indexOfFirst { isInvokeOf(it, "Lka/e;", "b") }
                check(idx >= 0) { "ka/e.b call not found in pa/e.q" }
                check(instructions.getOrNull(idx + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT) {
                    "ka/e.b call is not followed by move-result-object"
                }
                removeInstructions(idx, 2)
                addInstructions(
                    idx,
                    """
                    new-instance p1, Lka/a;
                    const-string v5, "offline"
                    invoke-direct {p1, v5}, Lka/a;-><init>(Ljava/lang/String;)V
                    """.trimIndent(),
                )
            }

        // yf/b awaits a pending-license-request flow before refreshing the licence.
        // Nothing ever emits on it without the donate-app activation, so the refresh
        // would park forever; jump straight to the request with no parameters instead.
        mutableClassDefBy("Lyf/b;")
            .methods.first { it.name == "q" }
            .apply {
                val impl = implementation!!
                val list = impl.instructions
                var branchIndex = -1
                for (i in 1 until list.size) {
                    val previous = list[i - 1]
                    if (previous is Instruction22c &&
                        previous.reference is FieldReference &&
                        (previous.reference as FieldReference).name == "r" &&
                        list[i].opcode == Opcode.IF_NEZ
                    ) {
                        branchIndex = i
                        break
                    }
                }
                check(branchIndex >= 0) { "request-flow await branch not found in yf/b.q" }
                val target = (list[branchIndex] as BuilderOffsetInstruction).target
                list[branchIndex] = BuilderInstruction10t(Opcode.GOTO, target)
            }

        // x5/e.R(boolean) clears the loading state only when the native hash in
        // GmhApp.x matches the runtime Adler-32 of the installed dex in GmhApp.y.
        // A patched dex can never match, which is what kept the app "loading" forever.
        mutableClassDefBy("Lx5/e;")
            .methods.first { it.name == "R" && it.parameterTypes == listOf("Z") }
            .apply {
                val impl = implementation!!
                val list = impl.instructions
                var branchIndex = -1
                for (i in 1 until list.size) {
                    if (list[i - 1].opcode == Opcode.CMP_LONG && list[i].opcode == Opcode.IF_EQZ) {
                        branchIndex = i
                        break
                    }
                }
                check(branchIndex >= 0) { "hash comparison branch not found in x5/e.R" }
                val target = (list[branchIndex] as BuilderOffsetInstruction).target
                list[branchIndex] = BuilderInstruction10t(Opcode.GOTO, target)
            }
    }
}
