/*
 * Copyright 2026 Calmdit contributors.
 * https://github.com/JustinZeus/calmdit-patches
 *
 * Calmdit addition to Morphe Patches (https://github.com/MorpheApp/morphe-patches).
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.postheader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.findFreeRegister
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
            "in the header of posts in the feeds. The post detail screen is not changed. " +
            "This patch works with Reddit 2026.40.0 and newer."
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

        // Usernames, in feeds that show the author instead of the subreddit.
        MetadataHeaderElementLabelFingerprint.replaceLabelText(
            "getUsernameLabel"
        )
        CompactPostHeaderFingerprint.replaceLabelText(
            "getCompactLabel"
        )

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

/**
 * Passes the text of the header label, created by the last matched constructor call,
 * through [extensionMethod] of the extension.
 */
private fun Fingerprint.replaceLabelText(extensionMethod: String) {
    method.apply {
        val index = instructionMatches.last().index
        // Parameters: label, text.
        val textRegister = getInstruction(index).registersUsed[1]

        addInstructionsAtControlFlowLabel(
            index,
            """
                invoke-static/range { v$textRegister .. v$textRegister }, $EXTENSION_CLASS->$extensionMethod(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v$textRegister
            """
        )
    }
}
