// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.android.ParsedAndroidData.KeyValueConsumer;
import com.google.devtools.build.android.proto.SerializeFormat;
import com.google.devtools.build.android.xml.AttrXmlResourceValue;
import com.google.devtools.build.android.xml.IdXmlResourceValue;
import com.google.devtools.build.android.xml.PluralXmlResourceValue;
import com.google.devtools.build.android.xml.SimpleXmlResourceValue;
import com.google.devtools.build.android.xml.StyleXmlResourceValue;
import com.google.devtools.build.android.xml.StyleableXmlResourceValue;
import com.google.protobuf.CodedOutputStream;

import com.android.resources.ResourceType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * {@link XmlResourceValues} provides methods for getting {@link XmlResourceValue} derived classes.
 *
 * <p>
 * Acts a static factory class containing the general xml parsing logic for resources that are
 * declared inside the &lt;resources&gt; tag.
 */
public class XmlResourceValues {
  private static final Logger logger = Logger.getLogger(XmlResourceValues.class.getCanonicalName());

  private static final QName TAG_EAT_COMMENT = QName.valueOf("eat-comment");
  private static final QName TAG_PLURALS = QName.valueOf("plurals");
  private static final QName ATTR_QUANTITY = QName.valueOf("quantity");
  private static final QName TAG_ATTR = QName.valueOf("attr");
  private static final QName TAG_DECLARE_STYLEABLE = QName.valueOf("declare-styleable");
  private static final QName TAG_ITEM = QName.valueOf("item");
  private static final QName TAG_STYLE = QName.valueOf("style");
  private static final QName TAG_SKIP = QName.valueOf("skip");
  private static final QName TAG_RESOURCES = QName.valueOf("resources");
  private static final QName ATTR_FORMAT = QName.valueOf("format");
  private static final QName ATTR_NAME = QName.valueOf("name");
  private static final QName ATTR_VALUE = QName.valueOf("value");
  private static final QName ATTR_PARENT = QName.valueOf("parent");
  private static final QName ATTR_TYPE = QName.valueOf("type");

  static XmlResourceValue parsePlurals(XMLEventReader eventReader) throws XMLStreamException {
    ImmutableMap.Builder<String, String> values = ImmutableMap.builder();
    for (XMLEvent element = nextTag(eventReader);
        !isEndTag(element, TAG_PLURALS);
        element = nextTag(eventReader)) {
      if (isItem(element)) {
        if (!element.isStartElement()) {
          throw new XMLStreamException(
              String.format("Expected start element %s", element), element.getLocation());
        }
        String contents = readContentsAsString(eventReader, element.asStartElement().getName());
        values.put(
            getElementAttributeByName(element.asStartElement(), ATTR_QUANTITY),
            contents == null ? "" : contents);
      }
    }
    return PluralXmlResourceValue.of(values.build());
  }

  static XmlResourceValue parseStyle(XMLEventReader eventReader, StartElement start)
      throws XMLStreamException {
    Map<String, String> values = new HashMap<>();
    for (XMLEvent element = nextTag(eventReader);
        !isEndTag(element, TAG_STYLE);
        element = nextTag(eventReader)) {
      if (isItem(element)) {
        values.put(getElementName(element.asStartElement()), eventReader.getElementText());
      }
    }
    String parent = parseReferenceFromElementAttribute(start, ATTR_PARENT, false);
    // Parents can be very lazily declared as just: <resource name>
    return StyleXmlResourceValue.of(parent, values);
  }

  static void parseDeclareStyleable(
      FullyQualifiedName.Factory fqnFactory,
      Path path,
      KeyValueConsumer<DataKey, DataResource> overwritingConsumer,
      KeyValueConsumer<DataKey, DataResource> combiningConsumer,
      XMLEventReader eventReader,
      StartElement start)
      throws XMLStreamException {
    List<String> members = new ArrayList<>();
    for (XMLEvent element = nextTag(eventReader);
        !isEndTag(element, TAG_DECLARE_STYLEABLE);
        element = nextTag(eventReader)) {
      if (isStartTag(element, TAG_ATTR)) {
        StartElement attr = element.asStartElement();
        String attrName = getElementName(attr);
        members.add(attrName);
        // If there is format and the next tag is a starting tag, treat it as an attr definition.
        // Without those, it will be an attr reference.
        if (XmlResourceValues.getElementAttributeByName(attr, ATTR_FORMAT) != null
            || (XmlResourceValues.peekNextTag(eventReader) != null
                && XmlResourceValues.peekNextTag(eventReader).isStartElement())) {
          overwritingConsumer.consume(
              fqnFactory.create(ResourceType.ATTR, attrName),
              DataResourceXml.of(path, parseAttr(eventReader, attr)));
        }
      }
    }
    combiningConsumer.consume(
        fqnFactory.create(ResourceType.STYLEABLE, getElementName(start)),
        DataResourceXml.of(path, StyleableXmlResourceValue.of(members)));
  }

  static XmlResourceValue parseAttr(XMLEventReader eventReader, StartElement start)
      throws XMLStreamException {
    XmlResourceValue value =
        AttrXmlResourceValue.from(
            start, getElementAttributeByName(start, ATTR_FORMAT), eventReader);
    return value;
  }

  static XmlResourceValue parseId() {
    return IdXmlResourceValue.of();
  }

  static XmlResourceValue parseSimple(
      XMLEventReader eventReader, ResourceType resourceType, StartElement start)
      throws XMLStreamException {
    // Using a map to deduplicate xmlns declarations on the attributes.
    Map<String, String> attributeMap = new LinkedHashMap<>();
    @SuppressWarnings({
      "cast",
      "unchecked"
    }) // The interface returns Iterator, force casting based on documentation.
    Iterator<Attribute> attributes = (Iterator<Attribute>) start.getAttributes();
    while (attributes.hasNext()) {
      Attribute attribute = attributes.next();
      QName name = attribute.getName();
      // Name used as the resource key, so skip it here.
      if (ATTR_NAME.equals(name)) {
        continue;
      }
      String value = escapeXmlValues(attribute.getValue()).replace("\"", "&quot;");
      if (!name.getNamespaceURI().isEmpty()) {
        // Declare the xmlns here, so that the written xml will be semantically correct,
        // if a bit verbose. This allows the resource keys to be written into a generic <resources>
        // tag.
        attributeMap.put("xmlns:" + name.getPrefix(), name.getNamespaceURI());
        attributeMap.put(name.getPrefix() + ":" + attribute.getName().getLocalPart(), value);
      } else {
        attributeMap.put(attribute.getName().getLocalPart(), value);
      }
    }
    String contents;
    // Check and see if the element is unary. If it is, the contents is null
    if (isEndTag(eventReader.peek(), start.getName())) {
      contents = null;
    } else {
      contents = readContentsAsString(eventReader, start.getName());
    }
    return SimpleXmlResourceValue.of(
        start.getName().equals(TAG_ITEM)
            ? SimpleXmlResourceValue.Type.ITEM
            : SimpleXmlResourceValue.Type.from(resourceType),
        ImmutableMap.copyOf(attributeMap),
        contents);
  }

  // TODO(corysmith): Replace this with real escaping system, preferably a performant high level xml
  //writing library. See AndroidDataWritingVisitor TODO.
  private static String escapeXmlValues(String data) {
    return data.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /**
   * Reads the xml events as a string until finding a closing tag.
   *
   * @param eventReader The current xml stream.
   * @param startTag The name of the tag to close on.
   * @return A xml escaped string representation of the xml stream
   */
  @Nullable
  public static String readContentsAsString(XMLEventReader eventReader, QName startTag)
      throws XMLStreamException {
    StringBuilder contents = new StringBuilder();
    while (!isEndTag(eventReader.peek(), startTag)) {
      XMLEvent xmlEvent = eventReader.nextEvent();
      if (xmlEvent.isCharacters()) {
        contents.append(escapeXmlValues(xmlEvent.asCharacters().getData()));
      } else if (xmlEvent.isStartElement()) {
        // TODO(corysmith): Replace this with a proper representation of the contents that can be
        // serialized and reconstructed appropriately without modification.
        QName name = xmlEvent.asStartElement().getName();
        contents.append("<");
        if (!name.getNamespaceURI().isEmpty()) {
          contents
              .append(name.getPrefix())
              .append(':')
              .append(name.getLocalPart())
              .append(' ')
              .append("xmlns:")
              .append(name.getPrefix())
              .append("='")
              .append(name.getNamespaceURI())
              .append("'");
        } else {
          contents.append(name.getLocalPart());
        }
        contents.append(">");
      } else if (xmlEvent.isEndElement()) {
        QName name = xmlEvent.asEndElement().getName();
        contents.append("</");
        if (!name.getNamespaceURI().isEmpty()) {
          contents.append(name.getPrefix()).append(':').append(name.getLocalPart());
        } else {
          contents.append(name.getLocalPart());
        }
        contents.append(">");
      }
    }
    // Verify the end element.
    EndElement endElement = eventReader.nextEvent().asEndElement();
    Preconditions.checkArgument(endElement.getName().equals(startTag));
    return contents.toString();
  }

  /* XML helper methods follow. */
  // TODO(corysmith): Move these to a wrapper class for XMLEventReader.
  private static String parseReferenceFromElementAttribute(
      StartElement element, QName name, boolean requiresPrefix) throws XMLStreamException {
    String value = getElementAttributeByName(element, name);
    if (value == null) {
      return null;
    }
    if (value.startsWith("?") || value.startsWith("@")) {
      return value.substring(1);
    }
    if (!requiresPrefix) {
      return value;
    }
    throw new XMLStreamException(
        String.format("Invalid resource reference from %s in %s", name, element),
        element.getLocation());
  }

  @Nullable
  public static String getElementAttributeByName(StartElement element, QName name) {
    Attribute attribute = element.getAttributeByName(name);
    return attribute == null ? null : attribute.getValue();
  }

  public static String getElementValue(StartElement start) {
    return getElementAttributeByName(start, ATTR_VALUE);
  }

  public static String getElementName(StartElement start) {
    return getElementAttributeByName(start, ATTR_NAME);
  }

  public static String getElementType(StartElement start) {
    return getElementAttributeByName(start, ATTR_TYPE);
  }

  public static boolean isTag(XMLEvent event, QName subTagType) {
    if (event.isStartElement()) {
      return isStartTag(event, subTagType);
    }
    if (event.isEndElement()) {
      return isEndTag(event, subTagType);
    }
    return false;
  }

  public static boolean isItem(XMLEvent start) {
    return isTag(start, TAG_ITEM);
  }

  public static boolean isEndTag(XMLEvent event, QName subTagType) {
    if (event.isEndElement()) {
      return subTagType.equals(event.asEndElement().getName());
    }
    return false;
  }

  public static boolean isStartTag(XMLEvent event, QName subTagType) {
    if (event.isStartElement()) {
      return subTagType.equals(event.asStartElement().getName());
    }
    return false;
  }

  public static XMLEvent nextTag(XMLEventReader eventReader) throws XMLStreamException {
    while (eventReader.hasNext()
        && !(eventReader.peek().isEndElement() || eventReader.peek().isStartElement())) {
      XMLEvent nextEvent = eventReader.nextEvent();
      if (nextEvent.isCharacters() && !nextEvent.asCharacters().isIgnorableWhiteSpace()) {
        Characters characters = nextEvent.asCharacters();
        // TODO(corysmith): Turn into a warning with the Path is available to add to it.
        // This case is when unexpected characters are thrown into the xml. Best case, it's a
        // incorrect comment type...
        logger.fine(
            String.format(
                "Invalid characters [%s] found at %s",
                characters.getData(),
                characters.getLocation().getLineNumber()));
      }
    }
    return eventReader.nextEvent();
  }

  public static XMLEvent peekNextTag(XMLEventReader eventReader) throws XMLStreamException {
    while (eventReader.hasNext()
        && !(eventReader.peek().isEndElement() || eventReader.peek().isStartElement())) {
      eventReader.nextEvent();
    }
    return eventReader.peek();
  }

  @Nullable
  static StartElement findNextStart(XMLEventReader eventReader) throws XMLStreamException {
    while (eventReader.hasNext()) {
      XMLEvent event = eventReader.nextEvent();
      if (event.isStartElement()) {
        return event.asStartElement();
      }
    }
    return null;
  }

  static boolean moveToResources(XMLEventReader eventReader) throws XMLStreamException {
    while (eventReader.hasNext()) {
      StartElement next = findNextStart(eventReader);
      if (next != null && next.getName().equals(TAG_RESOURCES)) {
        return true;
      }
    }
    return false;
  }

  public static SerializeFormat.DataValue.Builder newSerializableDataValueBuilder(Path source) {
    SerializeFormat.DataValue.Builder builder = SerializeFormat.DataValue.newBuilder();
    return builder.setSource(builder.getSourceBuilder().setFilename(source.toString()));
  }

  public static int serializeProtoDataValue(
      OutputStream output, SerializeFormat.DataValue.Builder builder) throws IOException {
    SerializeFormat.DataValue value = builder.build();
    value.writeDelimitedTo(output);
    return CodedOutputStream.computeUInt32SizeNoTag(value.getSerializedSize())
        + value.getSerializedSize();
  }

  public static boolean isEatComment(StartElement start) {
    return isTag(start, TAG_EAT_COMMENT);
  }

  public static boolean isSkip(StartElement start) {
    return isTag(start, TAG_SKIP);
  }
}
