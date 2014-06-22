package org.ticklefish.sisot;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import org.json.JSONObject;

/**
 * sisot search servlet which outputs CR-delim json.
 *
 * @author escowles
 * @since 2014-06-20
**/
public class SearchJSON extends AbstractServlet
{
	private static SimpleDateFormat df = new SimpleDateFormat("MM/dd HH:mm");
	public void init( ServletConfig cfg )
	{
		super.init(cfg);
	}
	protected void init( Properties props )
	{
		super.init(props);
	}

	public void doGet( HttpServletRequest req, HttpServletResponse res )
	{
		// if not configured, show config form
		if ( solr == null )
		{
			configForm(res); // TODO: return error message to prompt JSP
			return;
		}

		// setup query
		String re   = req.getParameter("re");
		String q    = req.getParameter("q");
		String tag  = req.getParameter("tag");
		String user = req.getParameter("user");

		int pageSize = 50;
		int page = 1;
		String pageStr = req.getParameter("page");
		if ( pop(pageStr) )
		{
			page = Integer.parseInt(pageStr);
		}
		int start = (page - 1) * 50;

		SolrQuery query = new SolrQuery();
		query.setStart( start );
		query.addSortField( "id", SolrQuery.ORDER.desc );
		query.setRows( pageSize );
		String stem = "?";
		if ( pop(re) )
		{
			stem += "re=" + re + "&";
		}
		if ( pop(tag) )
		{
			query.addFilterQuery("tag:" + tag);
			stem += "tag=" + tag + "&";
		}
		if ( pop(user) )
		{
			query.addFilterQuery("user_id:" + user);
			stem += "user=" + user + "&";
		}
		if ( q == null ) { q = ""; }
		else { stem += "q=" + q + "&"; }
		query.setQuery( q ); // null/blank?

		SolrDocumentList results = null;
		try
		{
			if ( re != null && !re.equals("-1") )
			{
				results = new SolrDocumentList();
				collect( results, re );
			}
			else
			{
				QueryResponse qr = solr.query( query );
				results = qr.getResults();
			}
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			results = null;
		}

		// set last-modified header
		if ( results != null && results.size() > 0 )
		{
			res.setDateHeader( "Last-Modified",
				((Date)results.get(0).getFirstValue("date")).getTime() );
		}
		else
		{
			res.setDateHeader( "Last-Modified", new Date().getTime() );
		}

		// setup output
		PrintWriter out = null;
		try
		{
			res.setContentType("text/html; charset=UTF-8");
			out = res.getWriter();
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			return;
		}

		stem += "page=";
		JSONObject pager = new JSONObject();
		if ( page > 1 )
		{
			pager.put( "prev", stem + (page - 1) );
		}
		pager.put( "current", page );
		if ( results.getNumFound() > (page*pageSize) )
		{
			pager.put( "next", stem + (page + 1) );
		}

		// output results
		out.println( "{" );
		out.println( "\"pager\":" + pager + "," );
		out.println( "\"results\":[");
		for ( int i = 0; results != null && i < results.size(); i++ )
		{
			out.print( json(results.get(i)) );
			if ( (i + 1) < results.size() )
			{
				out.println(",");
			}
		}
		out.println( "]" );
		out.println( "}" );

		// finish output
		out.flush();
		out.close();
	}
	private void collect( SolrDocumentList docs, String id )
		throws SolrServerException
	{
		SolrQuery query = new SolrQuery("id:" + id);
		SolrDocumentList results = solr.query(query).getResults();
		for ( SolrDocument doc : results )
		{
			docs.add( doc );

			// recursively find replies
			String replyTo = (String)doc.getFirstValue("re_id");
			if ( replyTo != null && !replyTo.equals("-1") )
			{
				collect( docs, replyTo );
			}
		}
	}

	private static boolean pop( String s )
	{
		return s != null && !s.trim().equals("");
	}

    private static String json( SolrDocument doc )
    {
		JSONObject json = new JSONObject();
		json.put( "user_id",    (String)doc.getFirstValue("user_id") );
		json.put( "user_name",  (String)doc.getFirstValue("user_name") );
		json.put( "user_image", (String)doc.getFirstValue("user_image") );
		json.put( "date",       df.format(doc.getFirstValue("date")) );
		json.put( "text",       (String)doc.getFirstValue("text") );
		json.put( "id",         (String)doc.getFirstValue("id") );
		json.put( "re_id",      (String)doc.getFirstValue("re_id") );

		Collection  media = doc.getFieldValues("media");
		List<String> mediaList = new ArrayList<>();
		if ( media != null )
		{
			for ( Iterator it = media.iterator(); it.hasNext(); )
			{
				mediaList.add( (String)it.next() );
			}
			json.put( "media", mediaList );
		}
		return json.toString();
    }
}
