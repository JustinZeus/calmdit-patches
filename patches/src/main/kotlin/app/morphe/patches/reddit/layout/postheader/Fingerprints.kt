/*
 * Copyright 2026 Calmdit contributors.
 * https://github.com/JustinZeus/calmdit-patches
 *
 * Calmdit addition to Morphe Patches (https://github.com/MorpheApp/morphe-patches).
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.postheader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/**
 * Call of the icon of a post header in the feeds, which shows the subreddit icon or the avatar
 * of the user. Parameters: image shape, background color, image url, on click, size, and more.
 */
private val headerIconCallFilter
    get() = methodCall(
        parameters = listOf(
            "Lcom/reddit/feeds/model/ImageShape;",
            "L",
            "Ljava/lang/String;",
            "Lkotlin/jvm/functions/Function0;",
            "Lcom/reddit/ui/compose/ds/AvatarSize;",
            "L",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "L",
            "Z",
            "Lkotlin/jvm/functions/Function0;",
            "Z",
            "Z",
            "L",
            "Z",
            "L",
            "I",
            "I",
            "I"
        ),
        returnType = "V",
        opcode = Opcode.INVOKE_STATIC_RANGE
    )

/**
 * Header of a post in the feeds with the card view. It shows the icon in one of two branches.
 */
internal object PostHeaderFingerprint : Fingerprint(
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
 * Header of a post in the feeds with the compact view, which shows the subreddit name,
 * or the username in feeds that show the author, such as a subreddit feed.
 * Parameters: post data, feed context, ..., feed type, and more.
 */
internal object CompactPostHeaderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "L",
        "L",
        "Z",
        "Lcom/reddit/feeds/data/FeedType;",
        "Ljava/lang/String;",
        "L",
        "Z",
        "Z",
        "Z",
        "Z",
        "Z",
        "Z",
        "Z",
        "L",
        "Z",
        "Z",
        "L",
        "I"
    ),
    filters = listOf(
        string("r/"),
        // Label, created with the text of the subreddit name or username.
        methodCall(
            definingClass = { MetadataHeaderElementLabelFingerprint.originalMethod.returnType },
            name = "<init>",
            parameters = listOf("Ljava/lang/String;"),
            returnType = "V"
        )
    )
)
