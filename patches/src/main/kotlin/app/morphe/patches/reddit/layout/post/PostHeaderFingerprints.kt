/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.post

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * Icon of a post header in the feeds, which shows the subreddit icon or the avatar of the user.
 * Parameters: image shape, background color, image url, on click, size, and more.
 * The parameters after these differ between app versions.
 */
internal object PostHeaderIconFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lcom/reddit/mediametrics/analytics/MediaPlacement;",
            name = "SNOOVATAR"
        ),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lcom/reddit/imagecomponent/avatar/api/AvatarPlacement;",
            name = "USER_IMAGE"
        )
    )
)

private val headerIconCallFilter
    get() = methodCall(reference = { PostHeaderIconFingerprint.originalMethod })

/**
 * Header of a post in the feeds with the card view. It shows the icon in one of two branches.
 */
internal object PostHeaderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        string("post_subreddit_icon"),
        headerIconCallFilter,
        string("post_subreddit_icon"),
        headerIconCallFilter
    )
)

/**
 * Header of a post in the feeds with the compact view.
 */
internal object CompactPostHeaderIconFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        string("post_classic_subreddit_icon"),
        headerIconCallFilter
    )
)

/**
 * toString of the data of a post header in the feeds with the card view.
 */
internal object MetadataHeaderElementToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("MetadataHeaderElement(linkId=")
    )
)

/**
 * Label of a post header in the feeds with the card view: the subreddit name,
 * or the username in feeds that show the author, such as a subreddit feed.
 * The username is created after removing its prefix.
 */
internal object MetadataHeaderElementLabelFingerprint : Fingerprint(
    classFingerprint = MetadataHeaderElementToStringFingerprint,
    parameters = listOf(),
    filters = listOf(
        // Checks if the label is the author.
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
            returnType = "Z",
            opcode = Opcode.INVOKE_STATIC
        ),
        // Removes the username prefix.
        methodCall(
            parameters = listOf("Ljava/lang/String;"),
            returnType = "Ljava/lang/String;",
            opcode = Opcode.INVOKE_STATIC,
            location = MatchAfterWithin(8)
        ),
        // Username label.
        methodCall(
            name = "<init>",
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
            returnType = "V",
            location = MatchAfterWithin(5)
        )
    )
)

/**
 * toString of the data of a post in the feeds with the compact view.
 */
internal object ClassicPostElementToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("ClassicPostElement(linkId=")
    )
)

/**
 * Header of a post in the feeds with the compact view, which shows the subreddit name,
 * or the username in feeds that show the author, such as a subreddit feed.
 * Parameters: post data, feed context, whether the post age is shown, feed type, and more.
 * The parameters after these differ between app versions.
 */
internal object CompactPostHeaderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        // A value of the post data.
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = { ClassicPostElementToStringFingerprint.originalClassDef.type }
        ),
        // Label, created with the text of the subreddit name or username.
        methodCall(
            definingClass = { MetadataHeaderElementLabelFingerprint.originalMethod.returnType },
            name = "<init>",
            parameters = listOf("Ljava/lang/String;"),
            returnType = "V"
        ),
        // If the post age is shown.
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately())
    )
)

/**
 * If the posts of a feed show their header with another layout (credit bar), which 2026.47.0+
 * uses for some feeds, such as subreddit feeds.
 * Parameters: feed type, credit bar disabled, and more. It returns false if the second
 * parameter is true.
 */
internal object CreditBarHeaderEnabledFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Lcom/reddit/feeds/data/FeedType;", "Z", "L", "L"),
    filters = listOf(
        methodCall(
            parameters = listOf("Lcom/reddit/feeds/data/FeedType;"),
            returnType = "Lcom/reddit/feeds/impl/ui/converters/FeedCreditBarSurface;"
        )
    )
)

internal const val MOD_ROLE_INDICATOR_CLASS = "Lcom/reddit/feeds/model/PostMetadataModRoleIndicator;"

private fun modRoleIndicatorFilter(name: String) = fieldAccess(
    opcode = Opcode.SGET_OBJECT,
    definingClass = MOD_ROLE_INDICATOR_CLASS,
    name = name
)

/**
 * Creates the data of a post header in the feeds with the card view, with the mod and admin
 * badges of the author from the status indicators of the post.
 */
internal object MetadataHeaderElementMapperFingerprint : Fingerprint(
    returnType = { MetadataHeaderElementToStringFingerprint.originalClassDef.type },
    filters = listOf(
        checkCast("Lcom/reddit/type/PostStatusIndicatorType;"),
        modRoleIndicatorFilter("MODERATOR"),
        modRoleIndicatorFilter("ADMIN")
    )
)

/**
 * Creates the data of a post in the feeds with the compact view, with the mod and admin
 * badges of the author from the status indicators of the post.
 */
internal object ClassicPostElementMapperFingerprint : Fingerprint(
    returnType = { ClassicPostElementToStringFingerprint.originalClassDef.type },
    filters = listOf(
        checkCast("Lcom/reddit/type/PostStatusIndicatorType;"),
        modRoleIndicatorFilter("MODERATOR"),
        modRoleIndicatorFilter("ADMIN")
    )
)

/**
 * Header of a post in the feeds with the compact view: the label, the post age and more,
 * and the menu button. The first parameter is the label.
 */
internal object CompactPostHeaderRowFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        // Text of the label.
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = { MetadataHeaderElementLabelFingerprint.originalMethod.returnType },
            type = "Ljava/lang/String;"
        ),
        string("post_classic_header")
    )
)
