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

@SlingServlet(paths = { "/system/opensocial/oauthdata" }, methods = { "GET" }, extensions = { "json" })
@Properties(value = {
		@Property(name = "service.vendor", value = "Edia"),
		@Property(name = "service.description", value = "retrieve oauth specific data for widget") })
public class OAuthWidgetServlet extends SlingAllMethodsServlet {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1621498979301180147L;
	
	public static enum PARAMS {
	    token
	  }
	
	@Reference
	protected transient Repository repository;

	
	@Override
	protected void doGet(SlingHttpServletRequest request,
			SlingHttpServletResponse response) throws ServletException,
			IOException {

		String token = request.getParameter(PARAMS.token.toString());
		
		
		
		
		StringWriter sw = new StringWriter();
		JSONWriter write = new JSONWriter(sw);
		try {


			ShindigTokenCrypter shindigToken = new ShindigTokenCrypter(token);
			
			Session session = repository.loginAdministrative();

			ContentManager contentManager = session.getContentManager();

			Content content = contentManager.get(LitePersonalUtils
					.getPrivatePath(shindigToken.getGadgetOwner())
					+ "/privspace/id"
					+ shindigToken.getWidgetId() + "/opensocial");
			
			
			write.object();
			
			write.key("openSocialConsumerKey");
			write.value(content.getProperty("openSocialConsumerKey"));
			
			write.key("openSocialConsumerSecret");
			write.value(content.getProperty("openSocialConsumerSecret"));
		
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
