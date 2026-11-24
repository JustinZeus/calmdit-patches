/*
 * Copyright 2026 Calmdit contributors.
 * https://github.com/JustinZeus/calmdit-patches
 *
 * Calmdit addition to Morphe Patches (https://github.com/MorpheApp/morphe-patches).
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import androidx.annotation.Nullable;

import app.morphe.extension.reddit.settings.Settings;
import app.morphe.extension.shared.Logger;

/**
 * Hides the username, the user avatar and the subreddit icon in the header of posts in the feeds.
 * <p>
 * The header shows the subreddit with its icon, or in feeds such as a subreddit feed,
 * the author with their avatar. The compact view shows the subreddit name or username
 * without an icon. The post age of a hidden username is hidden too, as it looks out of place
 * on its own.
 * <p>
 * Reddit 2026.47.0 and newer show the header of some feeds, such as subreddit feeds,
 * with another layout (credit bar). While any element is hidden, these feeds use the header
 * that the other feeds use.
 */
@SuppressWarnings("unused")
public final class HidePostHeaderElementsPatch {

    /**
     * Image shapes of the header icon that are only used for user avatars.
     * Other shapes are used for both, and are told apart by the image url.
     */
    private static final String SHAPE_SNOO_AVATAR = "HEX";
    private static final String SHAPE_USER_IMAGE = "ROUND_WITH_BORDER";

    /**
     * Url parts of user avatars: default avatars, uploaded images and Snoo avatars.
     */
    private static final String[] USER_AVATAR_URL_PARTS = {
            "avatar_default_",
            "profileIcon_",
            "snoovatar/avatars/"
    };

    private static final String USERNAME_PREFIX = "u/";

    private HidePostHeaderElementsPatch() {
    }

    /**
     * @return If this patch was included during patching.
     */
    @SuppressWarnings("SameReturnValue")
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point.
     *
     * @param disabled If the credit bar layout is disabled for the header of posts in a feed.
     */
    public static boolean disableCreditBarHeader(boolean disabled) {
        return disabled
                || Settings.HIDE_FEED_POST_USERNAME.get()
                || Settings.HIDE_FEED_POST_USER_AVATAR.get()
                || Settings.HIDE_FEED_POST_SUBREDDIT_ICON.get()
                || Settings.HIDE_FEED_POST_MOD_BADGE.get();
    }

    /**
     * Injection point. Called with the mod or admin badge of the author of a post in the feeds.
     *
     * @return The badge, or null if it is not shown.
     */
    @Nullable
    public static Object getModRoleIndicator(@Nullable Object indicator) {
        return Settings.HIDE_FEED_POST_MOD_BADGE.get() ? null : indicator;
    }

    private static boolean isUserAvatar(Object shape, @Nullable String url) {
        String shapeName = ((Enum<?>) shape).name();
        if (SHAPE_SNOO_AVATAR.equals(shapeName) || SHAPE_USER_IMAGE.equals(shapeName)) {
            return true;
        }

        if (url == null) return false;

        for (String part : USER_AVATAR_URL_PARTS) {
            if (url.contains(part)) return true;
        }
        return false;
    }

    /**
     * Injection point.
     *
     * @param shape Image shape enum of the icon.
     * @param color Background color of the icon, or null.
     * @param url   Image url of the icon, or null.
     * @return If the icon in the header of a post is not shown.
     */
    public static boolean hideIcon(Object shape, @Nullable Object color, @Nullable String url) {
        try {
            final boolean hideUserAvatar = Settings.HIDE_FEED_POST_USER_AVATAR.get();
            final boolean hideSubredditIcon = Settings.HIDE_FEED_POST_SUBREDDIT_ICON.get();
            if (!hideUserAvatar && !hideSubredditIcon) {
                return false;
            }

            return isUserAvatar(shape, url) ? hideUserAvatar : hideSubredditIcon;
        } catch (Exception ex) {
            Logger.printException(() -> "hideIcon failure", ex);
            return false;
        }
    }

    /**
     * Injection point. Called with the username that the header of a post in the card view shows
     * in feeds that show the author.
     */
    public static String getUsernameLabel(String username) {
        return Settings.HIDE_FEED_POST_USERNAME.get() ? "" : username;
    }

    /**
     * Injection point. Called by the header of a post in the card view that shows the username,
     * to also hide its post age.
     */
    public static boolean hideUsername() {
        return Settings.HIDE_FEED_POST_USERNAME.get();
    }

    /**
     * Injection point. Called with the subreddit name or username that the header of a post
     * in the compact view shows.
     *
     * @return If the username and its post age are not shown.
     */
    public static boolean hideCompactLabel(@Nullable String label) {
        return Settings.HIDE_FEED_POST_USERNAME.get()
                && label != null && label.startsWith(USERNAME_PREFIX);
    }
}
