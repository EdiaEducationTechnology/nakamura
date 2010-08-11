/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.util;

import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

import java.io.Writer;
import java.util.Map.Entry;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;

public class ExtendedJSONWriter extends JSONWriter {

  public ExtendedJSONWriter(Writer w) {
    super(w);
  }

  public void valueMap(ValueMap valueMap) throws JSONException {
    object();
    valueMapInternals(valueMap);
    endObject();
  }

  /**
   * This will output the key value pairs of a value map as JSON without opening and
   * closing braces, you will need to call object() and endObject() yourself but you can
   * use this to allow appending onto the end of the existing data
   * 
   * @param valueMap
   *          any ValueMap (cannot be null)
   * @throws JSONException
   *           on failure
   */
  public void valueMapInternals(ValueMap valueMap) throws JSONException {
    for (Entry<String, Object> entry : valueMap.entrySet()) {
      key(entry.getKey());
      Object entryValue = entry.getValue();
      if (entryValue instanceof Object[]) {
        array();
        Object[] objects = (Object[]) entryValue;
        for (Object object : objects) {
          value(object);
        }
        endArray();
      } else {
        value(entry.getValue());
      }
    }
  }

  public static void writeNodeContentsToWriter(JSONWriter write, Node node)
      throws RepositoryException, JSONException {
    // Since removal of bigstore we add in jcr:path and jcr:name
    write.key("jcr:path");
    write.value(translateAuthorizablePath(node.getPath()));
    write.key("jcr:name");
    write.value(node.getName());

    PropertyIterator properties = node.getProperties();
    while (properties.hasNext()) {
      Property prop = properties.nextProperty();
      String name = prop.getName();
      write.key(name);
      if (prop.getDefinition().isMultiple()) {
        Value[] values = prop.getValues();
        write.array();
        for (Value value : values) {
          Object ovalue = stringValue(value);
          if (isUserPath(name, ovalue)) {
            write.value(translateAuthorizablePath(ovalue));
          } else {
            write.value(ovalue);
          }
        }
        write.endArray();
      } else {
        Object value = stringValue(prop.getValue());
        if (isUserPath(name, value)) {
          write.value(translateAuthorizablePath(value));
        } else {
          write.value(value);
        }
      }
    }
  }

  private static boolean isUserPath(String name, Object value) {
    if ("jcr:path".equals(name) || "path".equals(name) || "userProfilePath".equals(name)) {
      String s = String.valueOf(value);
      if (s != null && s.length() > 4) {
        if (s.charAt(0) == '/' && s.charAt(1) == '_') {
          if (s.startsWith("/_user/") || s.startsWith("/_group/")) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static Object translateAuthorizablePath(Object value) {
    String s = String.valueOf(value);
    if (s != null && s.length() > 4) {
      if (s.charAt(0) == '/' && s.charAt(1) == '_') {
        String id = null;
        if (s.startsWith("/_user/") || s.startsWith("/_group/")) {
          int slash = s.indexOf('/', 2);
          while (slash > 0) {
            int nslash = s.indexOf('/', slash + 1);
            String nid = null;
            if (nslash > 0) {
              nid = s.substring(slash + 1, nslash);
            } else {
              nid = s.substring(slash + 1);
            }
            if (id == null) {
              id = nid;
            } else if (nid.equals(id)) {
              return "/~" + id + "/"+ s.substring(slash + 1);
            } else if (!nid.startsWith(id)) {
              return "/~" + id+ "/" + s.substring(slash + 1);
            }
            slash = nslash;
            id = nid;
          }
          if ( id != null && id.length() > 0) {
            return "/~" + id;
          }
        }
      }
    }
    return value;
  }

  public static void writeNodeToWriter(JSONWriter write, Node node) throws JSONException,
      RepositoryException {
    write.object();
    writeNodeContentsToWriter(write, node);
    write.endObject();
  }

  private static Object stringValue(Value value) throws ValueFormatException,
      IllegalStateException, RepositoryException {
    switch (value.getType()) {
    case PropertyType.STRING:
    case PropertyType.NAME:
    case PropertyType.REFERENCE:
    case PropertyType.PATH:
      return value.getString();
    case PropertyType.BOOLEAN:
      return value.getBoolean();
    case PropertyType.LONG:
      return value.getLong();
    case PropertyType.DOUBLE:
      return value.getDouble();
    case PropertyType.DATE:
      return DateUtils.iso8601(value.getDate());
    default:
      return value.toString();
    }
  }

  public void node(Node node) throws JSONException, RepositoryException {
    writeNodeToWriter(this, node);
  }

  /**
   * Represent an entire JCR tree in JSON format.
   * 
   * @param write
   *          The {@link JSONWriter writer} to send the data to.
   * @param node
   *          The node and it's subtree to output. Note: The properties of this node will
   *          be outputted as well.
   * @throws RepositoryException
   * @throws JSONException
   */
  public static void writeNodeTreeToWriter(JSONWriter write, Node node)
      throws RepositoryException, JSONException {
      writeNodeTreeToWriter(write, node, Boolean.FALSE);
  }
  
  /**
   * Represent an entire JCR tree in JSON format.
   * 
   * @param write
   *          The {@link JSONWriter writer} to send the data to.
   * @param node
   *          The node and it's subtree to output. Note: The properties of this node will
   *          be outputted as well.
   * @param objectInProgress
   *          use true if you don't want the method to enclose the output in fresh object braces
   * @throws RepositoryException
   * @throws JSONException
   */
  public static void writeNodeTreeToWriter(JSONWriter write, Node node, boolean objectInProgress)
      throws RepositoryException, JSONException {
    // Write this node's properties.
    if(!objectInProgress) {
        write.object();
    }
    writeNodeContentsToWriter(write, node);

    // Write all the child nodes.
    NodeIterator iterator = node.getNodes();
    while (iterator.hasNext()) {
      Node childNode = iterator.nextNode();
      write.key(childNode.getName());
      writeNodeTreeToWriter(write, childNode);
    }
    if(!objectInProgress) {
        write.endObject();
    }
  }

}
