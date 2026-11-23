/*
 * Copyright 2026 Calmdit contributors.
 * https://github.com/JustinZeus/calmdit-patches
 *
 * Calmdit addition to Morphe Patches (https://github.com/MorpheApp/morphe-patches).
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.postheader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.misc.version.is_2026_40_0_or_greater
import app.morphe.patches.reddit.misc.version.versionCheckPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.setExtensionIsPatchIncluded
import java.util.logging.Logger

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/HidePostHeaderElementsPatch;"

@Suppress("unused")
val hidePostHeaderElementsPatch = patch(
    name = "Hide post header elements",
    description = "Adds options to hide the username, the user avatar and the subreddit icon " +
            "in the header of posts in the feeds. Hiding the username also hides its post age. " +
            "The post detail screen is not changed. This patch works with Reddit 2026.40.0 and newer."
) {
    compatibleWith(COMPATIBILITY_REDDIT)

    dependsOn(settingsPatch, versionCheckPatch)

    execute {
        if (!is_2026_40_0_or_greater) {
            return@execute Logger.getLogger(this::class.java.name).warning(
                "'Hide post header elements' requires Reddit 2026.40.0+"
            )
        }

        // Icons: the card view header shows the icon in one of two branches.
        // From the last change to the first, so the indexes stay valid.
        PostHeaderFingerprint.let {
            it.hideIconCall(it.instructionMatches[3].index)
            it.hideIconCall(it.instructionMatches[1].index)
        }
        CompactPostHeaderIconFingerprint.let {
            it.hideIconCall(it.instructionMatches.last().index)
        }

        // Usernames and their post age, in feeds that show the author instead of the subreddit.
        // The post age is hidden too, as it looks out of place without the username.
        hideCardUsername()
        hideCompactUsername()

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}

/**
 * Skips the call of a header icon at [index] if the extension hides it.
 */
private fun Fingerprint.hideIconCall(index: Int) {
    method.apply {
        // Parameters: image shape, background color, image url. The registers are consecutive,
        // as the call uses a register range.
        val registers = getInstruction(index).registersUsed
        val freeRegister = findFreeRegister(index, registers)

        addInstructionsAtControlFlowLabel(
            index,
            """
                invoke-static/range { v${registers[0]} .. v${registers[2]} }, $EXTENSION_CLASS->hideIcon(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Z
                move-result v$freeRegister
                if-nez v$freeRegister, :hide_icon
            """,
            ExternalLabel("hide_icon", getInstruction(index + 1))
        )
    }
}

private fun hideCardUsername() {
    // Username label, which is created after removing the username prefix.
    val usernameLabelType = MetadataHeaderElementLabelFingerprint.let {
        it.method.apply {
            val index = it.instructionMatches.last().index
            // Parameters: label, text.
            val textRegister = getInstruction(index).registersUsed[1]

            addInstructionsAtControlFlowLabel(
                index,
                """
                    invoke-static/range { v$textRegister .. v$textRegister }, $EXTENSION_CLASS->getUsernameLabel(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$textRegister
                """
            )
        }

        it.instructionMatches.last().methodReference.definingClass
    }

    // Post age: the header does not show an empty post age. Parameters: label, author label,
    // domain label, details, post age, and more.
    // Changed at the start of the method, after the icon calls, so their indexes stay valid.
    PostHeaderFingerprint.method.apply {
        val register = getFreeRegisterProvider(0, 1).getFreeRegister4Bit()

        addInstructionsWithLabels(
            0,
            """
                move-object/from16 v$register, p0
                instance-of v$register, v$register, $usernameLabelType
                if-eqz v$register, :show_post_age
                invoke-static { }, $EXTENSION_CLASS->hideUsername()Z
                move-result v$register
                if-eqz v$register, :show_post_age
                const-string p4, ""
                :show_post_age
                nop
            """
        )
    }
}

private fun hideCompactUsername() {
    CompactPostHeaderFingerprint.let {
        it.method.apply {
            val index = it.instructionMatches.last().index
            // Parameters: label, text.
            val registers = getInstruction(index).registersUsed
            val textRegister = registers[1]
            val freeRegister = findFreeRegister(index, registers)

            // The header parameter at index 2 is whether the post age is shown.
            // Without it, the post age and the separator before it are not shown.
            addInstructionsAtControlFlowLabel(
                index,
                """
                    invoke-static/range { v$textRegister .. v$textRegister }, $EXTENSION_CLASS->hideCompactLabel(Ljava/lang/String;)Z
                    move-result v$freeRegister
                    if-eqz v$freeRegister, :show_label
                    const-string v$textRegister, ""
                    const/16 p2, 0x0
                    :show_label
                    nop
                """
            )
        }
    }
}
