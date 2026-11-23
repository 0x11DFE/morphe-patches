package app.fluffy.patches.galaxymaxhz.fix

import app.fluffy.patches.shared.Constants.COMPATIBILITY_GALAXYMAXHZ
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val fixStockCrashesPatch = bytecodePatch(
    name = "Fix Stock Crashes",
    description = "Fixes two crashes that also happen in the unpatched app: the refresh " +
        "rate slider throwing when its value range collapses to zero, and the refresh " +
        "rate overlay failing to reposition before it is attached to the window.",
) {
    compatibleWith(COMPATIBILITY_GALAXYMAXHZ)

    execute {
        // The slider validator throws IllegalStateException for degenerate input instead
        // of skipping the draw. Leave the validator on any violation.
        val sliderBase = mutableClassDefBy("Lq8/d;")
        val validator = sliderBase.methods.first { method ->
            method.instructions.any { instruction ->
                instruction is Instruction21c &&
                    (instruction).reference is StringReference &&
                    ((instruction).reference as StringReference).string
                        .contains("must be smaller than valueTo")
            }
        }
        validator.apply {
            val impl = implementation!!
            val throwStarts = impl.instructions.withIndex()
                .filter { (_, instruction) ->
                    instruction is Instruction21c &&
                        (instruction).reference is TypeReference &&
                        ((instruction).reference as TypeReference).type ==
                        "Ljava/lang/IllegalStateException;"
                }.map { it.index }
                .sortedDescending()
            check(throwStarts.isNotEmpty()) { "slider validator throws not found" }
            throwStarts.forEach { index ->
                impl.instructions.add(index, BuilderInstruction10x(Opcode.RETURN_VOID))
            }
        }

        // The overlay position update races window attachment; swallow the
        // "view not attached" failure instead of crashing.
        mutableClassDefBy("Lfa/r0;")
            .methods.first { it.name == "d" && it.parameterTypes.isEmpty() }
            .apply {
                val impl = implementation!!
                val updateIndex = impl.instructions.indexOfFirst { instruction ->
                    if (instruction !is ReferenceInstruction) return@indexOfFirst false
                    val reference = instruction.reference as? MethodReference ?: return@indexOfFirst false
                    reference.definingClass == "Landroid/view/ViewManager;" &&
                        reference.name == "updateViewLayout"
                }
                check(updateIndex >= 0) { "updateViewLayout call not found in fa/r0.d" }
                val end = impl.instructions.toList().size
                impl.addInstruction(BuilderInstruction11x(Opcode.MOVE_EXCEPTION, 0))
                impl.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
                val tryBlock = impl.addTryBlock(
                    impl.newLabelForAddress(updateIndex),
                    impl.newLabelForAddress(updateIndex + 1),
                )
                impl.addExceptionHandler(
                    tryBlock,
                    "Ljava/lang/IllegalArgumentException;",
                    impl.newLabelForAddress(end),
                )
            }
    }
}
