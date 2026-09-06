package dev.bytelore.server.content.admin.dto;

/** One rejected feed item in {@link WhitelistSourceFetchResponse#rejections()} (§5.7). */
public record RejectionItem(String versionString, String failedCheck, String detail) {}
