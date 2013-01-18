package nl.edia.nakamura.opensocial.shindig.servlet;

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.JSON_RESULTS;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;

import javax.servlet.ServletException;

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
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;

@SlingServlet(paths = { "/system/opensocial/personservice" }, methods = { "GET" }, extensions = { "json" })
@Properties(value = {
		@Property(name = "service.vendor", value = "Edia"),
		@Property(name = "service.description", value = "Gets list of contacts for the user as specified in the token") })
public class PersonServiceServlet extends SlingAllMethodsServlet {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1621498979301180147L;

	private static final String DEFAULT_SEARCH_PROC_TARGET = "(&("
			+ SolrSearchResultProcessor.DEFAULT_PROCESSOR_PROP + "=true))";
	@Reference(target = DEFAULT_SEARCH_PROC_TARGET)
	private transient SolrSearchResultProcessor defaultSearchProcessor;

	public static enum PARAMS {
	    token
	  }
	
	@Override
	protected void doGet(SlingHttpServletRequest request,
			SlingHttpServletResponse response) throws ServletException,
			IOException {

		String token = request.getParameter(PARAMS.token.toString());
		
		
		
		StringWriter sw = new StringWriter();
		JSONWriter write = new JSONWriter(sw);
		try {

			
			ShindigTokenCrypter st = new ShindigTokenCrypter(token);
			
			
			
			write.object();

			write.key(JSON_RESULTS);

			write.array();

			String q = "contactstorepath:a\\:" + st.getGadgetOwner() + "\\/contacts AND resourceType:sakai\\/contact AND state:(ACCEPTED)";
			Query query = new Query(q);
			try {

				Iterator<Result> it = defaultSearchProcessor
						.getSearchResultSet(request, query)
						.getResultSetIterator();

				while (it.hasNext()) {
					// Get the next row.
					Result result = it.next();

					// Write the result for this row.
					defaultSearchProcessor.writeResult(request, write, result);
				}
			} catch (SolrSearchException e) {
				response.sendError(e.getCode(), e.getMessage());
			}

			write.endArray();

			write.endObject();

			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(sw.getBuffer().toString());
		} catch (JSONException e) {
			throw new ServletException(e);
		}
		catch (BlobCrypterException e1) {
			throw new ServletException(e1);
		}

	}
}
