package dev.bytelore.server.pipeline;

import java.time.Instant;

/**
 * One entry parsed out of an Atom {@code <entry>} or RSS {@code <item>} element, before any
 * verification has run against it.
 *
 * @param id the feed's own item identifier ({@code <id>} or {@code <guid>}), or {@code null}
 * @param title the item title, or {@code null}
 * @param link the item's own link -- becomes the mandatory {@code source_url} of the drafted post
 * @param content the item body ({@code <content>}, {@code <summary>} or {@code <description>})
 * @param published the item's own timestamp, or {@code null} if it could not be parsed
 */
public record FeedItem(String id, String title, String link, String content, Instant published) {}
