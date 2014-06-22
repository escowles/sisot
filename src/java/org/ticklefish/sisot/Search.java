package org.ticklefish.sisot;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

/**
 * sisot search servlet
 *
 * @author escowles
 * @since 2014-06-20
**/
public class Search extends AbstractServlet
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
			configForm(res);
			return;
		}

		// do query
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
		StringBuffer header = new StringBuffer();
		query.setStart( start );
		query.addSortField( "id", SolrQuery.ORDER.desc );
		query.setRows( pageSize );
		String stem = "?";
		if ( pop(re) )
		{
			header.append( "<h1>conversation</h1>" );
			stem += "re=" + re + "&";
		}
		if ( pop(tag) )
		{
			query.addFilterQuery("tag:" + tag);
			stem += "tag=" + tag + "&";
			header.append(
				"<h1>tag: " + tag + " <a href=\"http://twitter.com/hashtag/"
				+ tag + "\"><img src=\"twitter.png\"/></a></h1>"
			);
		}
		if ( pop(user) )
		{
			query.addFilterQuery("user_id:" + user);
			stem += "user=" + user + "&";
			header.append(
				"<h1>user: " + user + " <a href=\"http://twitter.com/" + user
				+ "\"><img src=\"twitter.png\"/></a></h1>"
			);
		}
		if ( q == null ) { q = ""; }
		else { stem += "q=" + q + "&"; }
		query.setQuery( q ); // null/blank?
		header.append("<table id=\"header\"><tr><td id=\"search\">");
		header.append("<form><input name=\"q\" value=\"" + q + "\"/>");
		header.append("<input type=\"submit\" value=\"search\"/></form></td>");

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

		// start output
		out.println("<html>");
		out.println("<head>");
		out.println("<link rel=\"stylesheet\" type=\"text/css\" "
			+ "href=\"sisot.css\"></link>");
		out.println("</head>");
		out.println("<body>" );

		// output header
		out.println( header.toString() );

		stem += "page=";
		out.println("<td id=\"pager\">");
		if ( page > 1 )
		{
			out.println( "<a href=\"" + stem + (page - 1) + "\">prev</a> " );
		}
		out.println( page );
		if ( results.getNumFound() > (page*pageSize) )
		{
			out.println( " <a href=\"" + stem + (page + 1) + "\">next</a>" );
		}
		out.println("</td></tr></table>");

		// output results
		if ( results == null || results.size() == 0 )
		{
			out.println("<p>no results found</p>");
		}
		else
		{
			out.println("<table>");
			for ( int i = 0; results != null && i < results.size(); i++ )
			{
				out.println( format(results.get(i)) );
			}
			out.println("</table>");
		}

		// finish output
		out.println("</body>");
		out.println("</html>");
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

    private static String format( SolrDocument doc )
    {
		String userID    = (String)doc.getFirstValue("user_id");
		String userName  = (String)doc.getFirstValue("user_name");
		String userImage = (String)doc.getFirstValue("user_image");
		String date      = df.format(doc.getFirstValue("date"));
		String text      = (String)doc.getFirstValue("text");
		String id        = (String)doc.getFirstValue("id");
		String replyTo   = (String)doc.getFirstValue("re_id");
		Collection  media = doc.getFieldValues("media");

        StringBuffer buf = new StringBuffer();
        buf.append("<tr>");
        buf.append("<td>");
        buf.append(
			linkTo("?user=" + userID, "<img src=\"" + userImage + "\"/> ")
		);
        buf.append("</td>");
        buf.append("<td>" + date + " ");
        buf.append( linkTo("?user=" + userID, userName) + ": <br/>");
        buf.append( linkify(text) );
        buf.append("</td>");
		buf.append("<td>");
		if (  replyTo != null &&!replyTo.equals("-1") )
		{
			buf.append("<a href=\"?re=" + id + "\">");
			buf.append("<img src=\"conversation.png\"></a>");
		}
		if ( media != null )
		{
			for ( Iterator it = media.iterator(); it.hasNext(); )
			{
				String mediaURL = (String)it.next();
				buf.append(
					"<a href=\"" + mediaURL + "\"><img height=\"48\" "
					+ "width=\"48\" src=\"" + mediaURL + "\"/></a>"
				);
			}
		}
		buf.append("</td>");
        buf.append("</tr>");
        return buf.toString();
    }
    private static String linkify( String s )
    {
        StringBuffer buf = new StringBuffer();
        String[] words = s.split("\\s+");
        for ( int i = 0; i < words.length; i++ )
        {
            if ( words[i].startsWith("@") || words[i].startsWith(".@") )
            {
                String link = "?user=" + words[i].substring(
					words[i].indexOf("@"));
                buf.append( linkTo(link, words[i]) );
            }
            else if ( words[i].startsWith("#") )
            {
                String link = "?tag=" + words[i].substring(1);
                buf.append( linkTo(link, words[i]) );
            }
            else if ( words[i].startsWith("http://")
                   || words[i].startsWith("https://") )
            {
                buf.append( linkTo(words[i], words[i]) );
            }
            else
            {
                buf.append( words[i] );
            }
            if ( (i + 1) < words.length ) { buf.append(" "); }
        }
        return buf.toString();
    }
    private static String linkTo( String href, String s )
    {
        return "<a href=\"" + head(href) + "\">" + head(s) + "</a>" + tail(s);
    }
	private static String head( String s )
	{
		return s.matches(".*\\W$") ? s.substring(0, s.length() - 1) : s;
	}
	private static String tail( String s )
	{
		return s.matches(".*\\W$") ? s.substring(s.length() - 1) : "";
	}
}
