package org.sakaiproject.nakamura.files.search;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.solr.common.SolrInputDocument;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.lite.util.Iterables;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.ResourceIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@Component
public class PoolContentResourceTypeHandler implements IndexingHandler {

  private static final Set<String> WHITELIST_PROPERTIES = ImmutableSet.of(
      FilesConstants.POOLED_CONTENT_USER_MANAGER,
      FilesConstants.POOLED_CONTENT_USER_VIEWER, FilesConstants.POOLED_CONTENT_FILENAME,
      FilesConstants.POOLED_NEEDS_UPDATE, FilesConstants.SAKAI_FILE,
      FilesConstants.SAKAI_TAG_NAME, FilesConstants.SAKAI_TAG_UUIDS,
      FilesConstants.SAKAI_TAGS);
  private static final Set<String> IGNORE_NAMESPACES = ImmutableSet.of("jcr", "rep");
  private static final Set<String> IGNORE_PROPERTIES = ImmutableSet.of();
  private static final Map<String, String> INDEX_FIELD_MAP = getFieldMap();
  private static final Set<String> ARRAY_PROPERTIES = ImmutableSet.of(
      FilesConstants.POOLED_CONTENT_USER_MANAGER,
      FilesConstants.POOLED_CONTENT_USER_VIEWER, FilesConstants.SAKAI_TAGS,
      FilesConstants.SAKAI_TAG_UUIDS);

  private static final Logger LOGGER = LoggerFactory
      .getLogger(PoolContentResourceTypeHandler.class);
  @Reference
  protected ResourceIndexingService resourceIndexingService;

  private static Map<String, String> getFieldMap() {
    Builder<String, String> builder = ImmutableMap.builder();
    builder.put(FilesConstants.POOLED_CONTENT_USER_MANAGER, "manager");
    builder.put(FilesConstants.POOLED_CONTENT_USER_VIEWER, "viewer");
    builder.put(FilesConstants.POOLED_CONTENT_FILENAME, "filename");
    builder.put(FilesConstants.POOLED_NEEDS_UPDATE, "needsupdate");
    builder.put(FilesConstants.SAKAI_FILE, "file");
    builder.put(FilesConstants.SAKAI_TAG_NAME, "tagname");
    builder.put(FilesConstants.SAKAI_TAG_UUIDS, "taguuid");
    builder.put(FilesConstants.SAKAI_TAGS, "tag");
    return builder.build();
  }

  @Activate
  public void activate(Map<String, Object> properties) {
    resourceIndexingService.addHandler("sakai/content-pool", this, Session.class);
  }

  @Deactivate
  public void deactivate(Map<String, Object> properties) {
    resourceIndexingService.removeHander("sakai/content-pool", this, Session.class);
  }

  public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession,
      Event event) {
    LOGGER.debug("GetDocuments for {} ", event);
    String path = (String) event.getProperty("path");
    if (ignorePath(path)) {
      return Collections.emptyList();
    }
    List<SolrInputDocument> documents = Lists.newArrayList();
    if (path != null) {
      try {
        Session session = repositorySession.adaptTo(Session.class);
        ContentManager contentManager = session.getContentManager();
        Content content = contentManager.get(path);
        if (content != null) {
          SolrInputDocument doc = new SolrInputDocument();
          
          Map<String, Object> properties = content.getProperties();

          for (Entry<String, Object> p : properties.entrySet()) {
            String indexName = index(p);
            if (indexName != null) {
              for (Object o : convertToIndex(p)) {
                doc.addField(indexName, o);
              }
            }
          }

          InputStream contentStream = contentManager.getInputStream(path);
          if (contentStream != null) {
            doc.addField("content", contentStream);
          }
          
          for (String principal : getReadingPrincipals(session, path)) {
            doc.addField("readers", principal);
          }
          
          doc.addField("id", path);
          documents.add(doc);
        }
      } catch (ClientPoolException e) {
        LOGGER.warn(e.getMessage(), e);
      } catch (StorageClientException e) {
        LOGGER.warn(e.getMessage(), e);
      } catch (AccessDeniedException e) {
        LOGGER.warn(e.getMessage(), e);
      } catch (IOException e) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
    return documents;
  }

  private String[] getReadingPrincipals(Session session, String path) throws StorageClientException {
    AccessControlManager accessControlManager = session.getAccessControlManager();
    return accessControlManager.findPrincipals(Security.ZONE_CONTENT ,path, Permissions.CAN_READ.getPermission(), true);
  }

  public Collection<String> getDeleteQueries(RepositorySession repositorySession, Event event) {
    LOGGER.debug("GetDelete for {} ", event);
    String path = (String) event.getProperty("path");
    boolean ignore = ignorePath(path);
    if ( ignore ) {
      return Collections.emptyList();
    } else {
      return ImmutableList.of("id:" + path);
    }
  }
  
  public void setResourceIndexingService(ResourceIndexingService resourceIndexingService) {
    if (resourceIndexingService != null) {
      this.resourceIndexingService = resourceIndexingService;
    }
  }

  protected boolean ignorePath(String path) {
    return false;
  }

  private Iterable<?> convertToIndex(Entry<String, Object> p) {
    String name = p.getKey();
    if (ARRAY_PROPERTIES.contains(name)) {
      return Iterables.of(StorageClientUtils.toStringArray(p.getValue()));
    }
    return Iterables.of(new String[] { StorageClientUtils.toString(p.getValue()) });
  }

  protected String index(Entry<String, Object> e) {
    String name = e.getKey();
    String[] parts = StringUtils.split(name, ':');
    if (!WHITELIST_PROPERTIES.contains(name)) {
      if (IGNORE_NAMESPACES.contains(parts[0])) {
        return null;
      }
      if (IGNORE_PROPERTIES.contains(name)) {
        return null;
      }
    }
    String mappedName = INDEX_FIELD_MAP.get(name);
    // only fields in the map will be used, and those are in the schema.
    return mappedName;
  }

}
