package nl.edia.nakamura.opensocial.shindig.servlet;

import java.io.IOException;
import java.io.StringWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.shindig.auth.ShindigTokenCrypter;
import org.apache.shindig.common.crypto.BlobCrypterException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SlingServlet(paths = { "/system/opensocial/shindigtoken" }, methods = { "GET" }, extensions = { "json" })
@Properties(value = {
		@Property(name = "service.vendor", value = "Edia"),
		@Property(name = "service.description", value = "Gets the shindig token for the current user") })
public class ShindigTokenServlet extends SlingAllMethodsServlet {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1621498979301180147L;

	public static enum PARAMS {
		widgetId
	}

	@Reference
	protected transient Repository repository;

	private static final Logger LOGGER = LoggerFactory
			.getLogger(ShindigTokenServlet.class);

	@Override
	protected void doGet(SlingHttpServletRequest request,
			SlingHttpServletResponse response) throws ServletException,
			IOException {

		String widgetId = request.getParameter(PARAMS.widgetId.toString());

		if (!widgetId.substring(0, 2).equals("id")) {
			throw new ServletException(
					"wrong value for widgetId, should start with 'id'");
		}
		
		widgetId = widgetId.substring(2);
		

		StringWriter sw = new StringWriter();
		JSONWriter write = new JSONWriter(sw);
		try {

			Session session = StorageClientUtils.adaptToSession(request
					.getResourceResolver().adaptTo(javax.jcr.Session.class));
			AuthorizableManager authorizableManager = session
					.getAuthorizableManager();
			Authorizable currentUser = authorizableManager
					.findAuthorizable(session.getUserId());

			ContentManager contentManager = session.getContentManager();

			Content content = contentManager.get(LitePersonalUtils
					.getPrivatePath(currentUser.getId())
					+ "/privspace/id"
					+ widgetId + "/opensocial");
			LOGGER.debug(content.toString());

			write.object();

			ShindigTokenCrypter shindigToken = new ShindigTokenCrypter(currentUser.getId(),
					currentUser.getId(), widgetId,
					(String) content.getProperty("openSocialXmlUrl"));

			write.key("token");
			write.value(shindigToken.getEncryptedTokenForUrl());

			write.endObject();

			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(sw.getBuffer().toString());
		} catch (StorageClientException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Error using the access control manager");
		} catch (AccessDeniedException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN,
					"Insufficient permission to use the access control manager");
		} catch (JSONException e) {
			throw new ServletException(e);
		} catch (BlobCrypterException e) {
			throw new ServletException(e);
		}
	}
}
