package dev.bytelore.server.pipeline;

import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Parses an Atom feed ({@code <feed><entry>...}) or an RSS feed ({@code <rss><channel><item>...})
 * into {@link FeedItem}s.
 *
 * <p>A feed is untrusted remote input, so the parser is hardened against XXE: DOCTYPE declarations
 * are refused outright and external entity resolution is disabled. A feed that fails to parse --
 * malformed XML, an unrecognised root element, or a document that trips the security limits --
 * yields an empty list rather than throwing, so one broken source cannot fail the whole fetch cycle
 * or crash the scheduler.
 */
public final class FeedParser {

  private static final Logger log = LoggerFactory.getLogger(FeedParser.class);

  private FeedParser() {}

  public static List<FeedItem> parse(String xml) {
    if (xml == null || xml.isBlank()) {
      return List.of();
    }
    try {
      Document document = documentBuilder().parse(new InputSource(new StringReader(xml)));
      Element root = document.getDocumentElement();
      if (root == null) {
        return List.of();
      }
      String rootName = localName(root);
      if ("feed".equalsIgnoreCase(rootName)) {
        return parseEntries(root, "entry");
      }
      if ("rss".equalsIgnoreCase(rootName) || "channel".equalsIgnoreCase(rootName)) {
        return parseEntries(root, "item");
      }
      log.debug("Unrecognised feed root element '{}'; treating the feed as malformed.", rootName);
      return List.of();
    } catch (Exception e) {
      log.warn("Failed to parse feed body as Atom/RSS XML: {}", e.getMessage());
      return List.of();
    }
  }

  private static DocumentBuilder documentBuilder() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder();
  }

  private static List<FeedItem> parseEntries(Element root, String itemTag) {
    List<FeedItem> items = new ArrayList<>();
    NodeList nodes = root.getElementsByTagName(itemTag);
    for (int i = 0; i < nodes.getLength(); i++) {
      if (!(nodes.item(i) instanceof Element entry)) {
        continue;
      }
      String id = firstNonBlank(text(entry, "id"), text(entry, "guid"));
      String title = text(entry, "title");
      String link = extractLink(entry);
      String content =
          firstNonBlank(text(entry, "content"), text(entry, "summary"), text(entry, "description"));
      Instant published =
          parseInstant(
              firstNonBlank(
                  text(entry, "published"), text(entry, "updated"), text(entry, "pubDate")));
      items.add(new FeedItem(id, title, link, content, published));
    }
    return items;
  }

  /**
   * Atom encodes a link as {@code <link href="..."/>}; RSS encodes it as {@code <link>text</link>}.
   * Both are tried, direct children first, so a nested element belonging to something else (an
   * embedded entry, say) is not picked up by accident.
   */
  private static String extractLink(Element entry) {
    NodeList children = entry.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (!(child instanceof Element element) || !"link".equalsIgnoreCase(localName(element))) {
        continue;
      }
      String href = element.getAttribute("href");
      if (href != null && !href.isBlank()) {
        return href.trim();
      }
      String text = element.getTextContent();
      if (text != null && !text.isBlank()) {
        return text.trim();
      }
    }
    return null;
  }

  private static String text(Element parent, String tag) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child instanceof Element element && tag.equalsIgnoreCase(localName(element))) {
        String value = element.getTextContent();
        return value == null ? null : value.trim();
      }
    }
    return null;
  }

  private static String localName(Element element) {
    String local = element.getLocalName();
    return local != null ? local : element.getTagName();
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static Instant parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (Exception ignoredIso) {
      try {
        return java.time.OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant();
      } catch (Exception ignoredRfc) {
        return null;
      }
    }
  }
}
