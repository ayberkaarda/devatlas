package dev.devatlas.server.pipeline;

/**
 * One rejected feed item, as reported in the manual fetch trigger's response (§5.7): {@code
 * rejections[]}.
 *
 * @param versionString the extracted version string, or {@code null} if none could be extracted
 * @param failedCheck the name of the first check that failed
 * @param detail human-readable detail
 */
public record Rejection(String versionString, String failedCheck, String detail) {}
