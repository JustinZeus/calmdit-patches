/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.post

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.misc.version.is_2026_47_0_or_greater
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/HidePostHeaderElementsPatch;"

private const val EXTENSION_USERNAME_LABEL_INTERFACE =
    $$"Lapp/morphe/extension/reddit/patches/HidePostHeaderElementsPatch$UsernameLabel;"

/**
 * Hides the username, the user avatar, the subreddit icon and the mod badge in the header of
 * posts in the feeds, and the whole header of posts that show the author, such as in subreddit
 * feeds. Requires Reddit 2026.40.0+.
 */
internal fun hidePostHeaderElements() {
    // Icons: the card view header shows the icon in one of two branches. Its label tells if
    // the icon is the avatar of the author or the subreddit icon. The label parameter is
    // never changed in the method. The compact view header only shows subreddit icons.
    // From the last change to the first, so the indexes stay valid.
    PostHeaderFingerprint.let {
        it.requireParameter(0, MetadataHeaderElementLabelFingerprint.originalMethod.returnType)

        val hideCardIcon =
            "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->hideCardIcon(Ljava/lang/Object;)Z"
        it.hideIconCall(it.instructionMatches[3].index, hideCardIcon)
        it.hideIconCall(it.instructionMatches[1].index, hideCardIcon)
    }
    CompactPostHeaderIconFingerprint.let {
        it.hideIconCall(
            it.instructionMatches.last().index,
            "invoke-static { }, $EXTENSION_CLASS->hideCompactIcon()Z"
        )
    }

    // 2026.47.0+ shows the header of some feeds, such as subreddit feeds, with another
    // layout (credit bar). Use the layout that the hooks above change while any element
    // is hidden.
    if (is_2026_47_0_or_greater) {
        CreditBarHeaderEnabledFingerprint.method.addInstructions(
            0,
            """
                invoke-static { p1 }, $EXTENSION_CLASS->disableCreditBarHeader(Z)Z
                move-result p1
            """
        )
    }

    // Usernames and their post age, in feeds that show the author instead of the subreddit.
    // The post age is hidden too, as it looks out of place without the username.
    // The whole header of these posts can be hidden too.
    hideCardUsername()
    hideCompactUsername()
    hideCompactHeaderRow()

    // Mod and admin badges of the author, which the data of the posts no longer gets.
    listOf(
        MetadataHeaderElementMapperFingerprint,
        ClassicPostElementMapperFingerprint
    ).forEach { it.hideModRoleIndicators() }

    setExtensionIsPatchIncluded(EXTENSION_CLASS)
}

/**
 * Passes the mod and admin badges, which the matched filters read, through the extension.
 */
private fun Fingerprint.hideModRoleIndicators() {
    method.apply {
        // The mod badge, then the admin badge, after the status indicator. From the last change
        // to the first, so the indexes stay valid.
        listOf(instructionMatches[2], instructionMatches[1]).forEach { match ->
            val register = getInstruction<OneRegisterInstruction>(match.index).registerA

            addInstructions(
                match.index + 1,
                """
                    invoke-static/range { v$register .. v$register }, $EXTENSION_CLASS->getModRoleIndicator(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$register
                    check-cast v$register, $MOD_ROLE_INDICATOR_CLASS
                """
            )
        }
    }
}

/**
 * Skips the call of a header icon at [index] if the extension method that [hideIcon] calls
 * returns true.
 */
private fun Fingerprint.hideIconCall(index: Int, hideIcon: String) {
    method.apply {
        val freeRegister = findFreeRegister(index)

        addInstructionsAtControlFlowLabel(
            index,
            """
                $hideIcon
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

    // Mark the username label, so the extension can tell which header icon is an avatar.
    mutableClassDefBy(usernameLabelType).interfaces.add(EXTENSION_USERNAME_LABEL_INTERFACE)

    // Header of posts that show the username, and the post age: the header does not show
    // an empty post age. Parameters: label, author label, domain label, details, post age,
    // and more.
    // Changed at the start of the method, after the icon calls, so their indexes stay valid.
    PostHeaderFingerprint.let {
        it.requireParameter(0, MetadataHeaderElementLabelFingerprint.originalMethod.returnType)
        it.requireParameter(4, "Ljava/lang/String;")
    }
    PostHeaderFingerprint.method.apply {
        val register = getFreeRegisterProvider(0, 1).getFreeRegister4Bit()

        addInstructionsWithLabels(
            0,
            """
                move-object/from16 v$register, p0
                instance-of v$register, v$register, $usernameLabelType
                if-eqz v$register, :show_post_age
                invoke-static { }, $EXTENSION_CLASS->hidePostHeader()Z
                move-result v$register
                if-eqz v$register, :show_header
                return-void
                :show_header
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

private fun hideCompactHeaderRow() {
    CompactPostHeaderRowFingerprint.let {
        // The first parameter is the label.
        it.requireParameter(0, MetadataHeaderElementLabelFingerprint.originalMethod.returnType)

        it.method.apply {
            val labelTextField = it.instructionMatches.first().fieldReference
            val register = getFreeRegisterProvider(0, 1).getFreeRegister4Bit()

            addInstructionsWithLabels(
                0,
                """
                    invoke-static { }, $EXTENSION_CLASS->hidePostHeader()Z
                    move-result v$register
                    if-eqz v$register, :show_header
                    move-object/from16 v$register, p0
                    if-eqz v$register, :show_header
                    iget-object v$register, v$register, $labelTextField
                    invoke-static { v$register }, $EXTENSION_CLASS->hideCompactPostHeader(Ljava/lang/String;)Z
                    move-result v$register
                    if-eqz v$register, :show_header
                    return-void
                    :show_header
                    nop
                """
            )
        }
    }
}

private fun hideCompactUsername() {
    CompactPostHeaderFingerprint.let {
        it.method.apply {
            val labelIndex = it.instructionMatches[1].index
            // Parameters: label, text.
            val registers = getInstruction(labelIndex).registersUsed
            val textRegister = registers[1]
            val freeRegister = findFreeRegister(labelIndex, registers)
            // The check after the label of whether the post age is shown, which is the header
            // parameter at index 2. Without it, the post age and the separator before it
            // are not shown.
            val showPostAgeRegister =
                getInstruction<OneRegisterInstruction>(it.instructionMatches[2].index).registerA

            addInstructionsAtControlFlowLabel(
                labelIndex,
                """
                    invoke-static/range { v$textRegister .. v$textRegister }, $EXTENSION_CLASS->hideCompactLabel(Ljava/lang/String;)Z
                    move-result v$freeRegister
                    if-eqz v$freeRegister, :show_label
                    const-string v$textRegister, ""
                    const/16 v$showPostAgeRegister, 0x0
                    :show_label
                    nop
                """
            )
        }
    }
}

/**
 * Fails patching if the parameter at [index] of the matched method is not of [type],
 * as the changes use the register of that parameter.
 */
private fun Fingerprint.requireParameter(index: Int, type: String) {
    val actualType = originalMethod.parameterTypes.getOrNull(index)?.toString()
    if (actualType != type) {
        throw PatchException("Parameter $index of ${originalMethod.name} is $actualType instead of $type")
    }
}
