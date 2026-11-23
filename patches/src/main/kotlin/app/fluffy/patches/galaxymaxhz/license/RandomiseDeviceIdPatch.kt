package app.fluffy.patches.galaxymaxhz.license

import app.fluffy.patches.shared.Constants.COMPATIBILITY_GALAXYMAXHZ
import app.fluffy.patches.waypoint.clearBody
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val randomiseDeviceIdPatch = bytecodePatch(
    name = "Randomise Device ID",
    description = "Replaces the fixed salt in the native device fingerprint with a random " +
        "value generated on every app start, so the device id sent to the licensing server " +
        "changes each run. The local license check does not use the salt and keeps working.",
) {
    compatibleWith(COMPATIBILITY_GALAXYMAXHZ)

    execute {
        // t0.c1.hh() feeds libgmh-lib.so a reflection spec plus a digest salt:
        // [key, class, method, signature, salt]. The native side digests
        // android_id + salt (+ the APK signature) into the device id. Only the
        // salt is replaced with a per-process random UUID; the reflection spec
        // stays stock so the lookup and the local verdicts are unaffected.
        // The key is a plain const-string, so no build-specific seed is needed.
        mutableClassDefBy("Lt0/c1;")
            .methods.first { it.name == "hh" && it.returnType == "Ljava/util/List;" }
            .apply {
                clearBody()
                addInstructions(
                    0,
                    """
                        const/4 v0, 0x5
                        new-array v0, v0, [Ljava/lang/String;
                        const/4 v3, 0x0
                        const-string v1, "android_id"
                        aput-object v1, v0, v3
                        const/4 v3, 0x1
                        const-string v1, "android/provider/Settings${'$'}Secure"
                        aput-object v1, v0, v3
                        const/4 v3, 0x2
                        const-string v1, "getString"
                        aput-object v1, v0, v3
                        const/4 v3, 0x3
                        const-string v1, "(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;"
                        aput-object v1, v0, v3
                        const-string v1, "gmh.rid"
                        invoke-static {v1}, Ljava/lang/System;->getProperty(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v4
                        if-nez v4, :have
                        invoke-static {}, Ljava/util/UUID;->randomUUID()Ljava/util/UUID;
                        move-result-object v4
                        invoke-virtual {v4}, Ljava/util/UUID;->toString()Ljava/lang/String;
                        move-result-object v4
                        const-string v1, "gmh.rid"
                        invoke-static {v1, v4}, Ljava/lang/System;->setProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                        :have
                        const/4 v3, 0x4
                        aput-object v4, v0, v3
                        invoke-static {v0}, La/b;->D([Ljava/lang/Object;)Ljava/util/List;
                        move-result-object v0
                        return-object v0
                    """,
                )
            }
    }
}
