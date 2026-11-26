/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import androidx.annotation.Nullable;

import app.morphe.extension.reddit.settings.Settings;

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
     * Added during patching to the label of a post header that shows the username.
     */
    public interface UsernameLabel {
    }

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
                || Settings.HIDE_FEED_POST_MOD_BADGE.get()
                || Settings.HIDE_FEED_POST_HEADER.get();
    }

    /**
     * Injection point. Called by the header of a post in the card view that shows the username,
     * and by the header of a post in the compact view.
     *
     * @return If the header of posts that show the author is not shown.
     */
    public static boolean hidePostHeader() {
        return Settings.HIDE_FEED_POST_HEADER.get();
    }

    /**
     * Injection point. Called by the header of a post in the compact view.
     *
     * @param label Subreddit name or username of the header.
     * @return If the header is not shown.
     */
    public static boolean hideCompactPostHeader(@Nullable String label) {
        return label != null && label.startsWith(USERNAME_PREFIX);
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

    /**
     * Injection point. Called by the header of a post in the card view before it shows its icon.
     *
     * @param label Label of the header. Its icon is the avatar of the author if the label
     *              is the username, or else the subreddit icon.
     * @return If the icon is not shown.
     */
    public static boolean hideCardIcon(Object label) {
        return label instanceof UsernameLabel
                ? Settings.HIDE_FEED_POST_USER_AVATAR.get()
                : Settings.HIDE_FEED_POST_SUBREDDIT_ICON.get();
    }

    /**
     * Injection point. Called by the header of a post in the compact view before it shows its
     * icon, which is the subreddit icon.
     *
     * @return If the icon is not shown.
     */
    public static boolean hideCompactIcon() {
        return Settings.HIDE_FEED_POST_SUBREDDIT_ICON.get();
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
     * in the compact view shows. If the whole header of the post is hidden, the label keeps
     * the username, so the header can tell that it shows the author.
     *
     * @return If the username and its post age are not shown.
     */
    public static boolean hideCompactLabel(@Nullable String label) {
        return Settings.HIDE_FEED_POST_USERNAME.get()
                && !Settings.HIDE_FEED_POST_HEADER.get()
                && label != null && label.startsWith(USERNAME_PREFIX);
    }
}
