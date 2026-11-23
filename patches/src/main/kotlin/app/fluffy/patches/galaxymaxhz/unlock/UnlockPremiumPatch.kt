package app.fluffy.patches.galaxymaxhz.unlock

import app.fluffy.patches.shared.Constants.COMPATIBILITY_GALAXYMAXHZ
import app.fluffy.patches.waypoint.clearBody
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks all premium features by forcing the license verdicts to true. " +
        "The native license library and its tamper checks are kept out of the loop, so the " +
        "patched and re-signed APK runs normally.",
) {
    compatibleWith(COMPATIBILITY_GALAXYMAXHZ)

    execute {
        val validator = mutableClassDefBy("Lt0/c1;")

        // g1/h1 wrap the native verdicts g/h; force premium.
        validator.methods.first { it.name == "g1" && it.returnType == "Z" }.apply {
            clearBody()
            addInstructions(0, "const/4 p1, 0x1\nreturn p1")
        }
        validator.methods.first { it.name == "h1" && it.returnType == "Z" }.apply {
            clearBody()
            addInstructions(0, "const/4 p1, 0x1\nreturn p1")
        }

        // f1 delegates to native f; build the license-info object in Java with premium set.
        validator.methods.first {
            it.name == "f1" && it.returnType == "Landroidx/appcompat/util/SeslUtil;"
        }.apply {
            clearBody()
            addInstructions(
                0,
                """
                    const/4 p6, 0x1
                    new-instance p0, Landroidx/appcompat/util/SeslUtil;
                    invoke-direct {p0, p4, p5, p2, p6}, Landroidx/appcompat/util/SeslUtil;-><init>(Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/Long;Z)V
                    return-object p0
                """,
            )
        }

        // i2/z call native request builders; return empty JSON instead.
        val emptyJson = "new-instance p0, Lorg/json/JSONObject;\n" +
            "invoke-direct {p0}, Lorg/json/JSONObject;-><init>()V\n" +
            "return-object p0"
        validator.methods.first {
            it.name == "i2" && it.returnType == "Lorg/json/JSONObject;"
        }.apply {
            clearBody()
            addInstructions(0, emptyJson)
        }
        validator.methods.first {
            it.name == "z" && it.returnType == "Lorg/json/JSONObject;"
        }.apply {
            clearBody()
            addInstructions(0, emptyJson)
        }

        // The license-info data class reports premium regardless of stored state.
        mutableClassDefBy("Landroidx/appcompat/util/SeslUtil;")
            .methods.first {
                it.name == "<init>" &&
                    it.parameterTypes == listOf(
                        "Ljava/lang/Integer;",
                        "Ljava/lang/String;",
                        "Ljava/lang/Long;",
                        "Z",
                    )
            }
            .apply { addInstructions(0, "const/4 p4, 0x1") }

        // GmhApp.onCreate runs the native tamper init via c1.s(context); drop the call.
        val app = mutableClassDefBy("Lcom/tribalfs/gmh/application/GmhApp;")
        app.methods.first { it.name == "onCreate" && it.returnType == "V" }.apply {
            val idx = instructions.indexOfFirst { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference
                reference is MethodReference &&
                    reference.definingClass == "Lt0/c1;" &&
                    reference.name == "s"
            }
            check(idx >= 0) { "c1.s(Context) call not found in GmhApp.onCreate" }
            removeInstructions(idx, 1)
            addInstructions(idx, "nop")
        }
    }
}
