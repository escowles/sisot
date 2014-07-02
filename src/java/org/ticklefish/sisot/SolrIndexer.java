package org.ticklefish.sisot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Sisot indexer.
 *
 * @author escowles
 * @since 2014-07-01
**/
public class SolrIndexer extends AbstractServlet implements Runnable
{
	private SimpleDateFormat utcFormat = new SimpleDateFormat(
		"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
	);
	private SimpleDateFormat jsonFormat = new SimpleDateFormat(
		"EEE MMM dd HH:mm:ss z yyyy"
	);
	private SimpleDateFormat localFormat = new SimpleDateFormat("MM/dd HH:mm");
	private File dir;
	public void init( ServletConfig cfg )
	{
		super.init(cfg);
		init(props);
		utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        // schedule execution
        ScheduledExecutorService exec = new ScheduledThreadPoolExecutor(1);
        exec.scheduleWithFixedDelay(this, 0, 20, TimeUnit.MINUTES);
	}
	protected void init( Properties props )
	{
		super.init(props);
		dir = new File( props.getProperty("data.dir") );
	}

	public void run()
	{
        // make sure we're configured
        if ( solr == null )
        {
            System.out.println("Not configured, will retry in 30 minutes");
            return;
        }

		index(last(), new PrintWriter(System.out));
	}

	public void doGet( HttpServletRequest req, HttpServletResponse res )
	{
		// if we're not configured, redirect to config form
		if ( solr == null )
		{
			try
			{
				res.sendRedirect("config");
			}
			catch ( Exception ex )
			{
				ex.printStackTrace();
			}
			return;
		}

		// setup output
		PrintWriter out = null;
		try
		{
			res.setContentType("text/html");
			out = res.getWriter();
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			return;
		}

		// output html header
		out.println("<html>");
		out.println("<head>");
		out.println("<link rel=\"stylesheet\" href=\"sisot.css\"></link>");
		out.println("</head>");
		out.println("<body>");
		out.println("<h1>indexer</h1>");
		out.println("<form method=\"GET\">");
		out.println("<input type=\"submit\" name=\"reindex\" value=\"reindex all records\"/>");
		out.println("<input type=\"submit\" value=\"index new records\"/>");
		out.println("</form>");
		out.println("<pre>");
		out.println("reindexing");
		out.flush();

		// check reindex parameter to trigger full reindex
		String last = null;
		String reindex = req.getParameter("reindex");
		if ( reindex == null || reindex.trim().equals("") )
		{
			last = last();
		}
		index(last, out);

		// finish output
		out.println("</pre>");
		out.println("</body></html>");
		out.flush();
	}

	private String last()
	{
		// get last tweet id
        String last = "";
        try
        {
            // lookup last-indexed tweet
            SolrQuery query = new SolrQuery();
            query.addSortField( "id", SolrQuery.ORDER.desc );
            query.setFields( "id" );
            query.setRows( 1 );
            SolrDocumentList results = solr.query(query).getResults();
            if ( results.size() > 0 )
            {
                last = (String)results.get(0).getFirstValue("id");
            }
        }
        catch ( Exception ex )
        {
            ex.printStackTrace();
        }
		return last;
	}

	private void index( String since, PrintWriter out )
	{
		int records = 0;
		try
		{
			long start = System.currentTimeMillis();
			File[] jsonFiles = dir.listFiles();
			for ( int i = 0; i < jsonFiles.length; i++ )
			{
				File f = jsonFiles[i];
				if ( f.getName().endsWith(".json") )
				{
					String id = f.getName().replaceAll(".json","");
					if ( since == null || since.compareTo(id) < 0 )
					{
						solr.add( toDocument(f) );
						records++;
						if ( records % 100 == 0 )
						{
							long dur = System.currentTimeMillis() - start;
							out.println("indexed " + records + " tweets in "
								+ dur + " msec");
							out.flush();
						}
					}
				}
			}

			long dur = System.currentTimeMillis() - start;
			out.println("indexed " + records + " tweets in " + dur + " msec");
			out.flush();
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			out.println("error indexing: " + ex.toString());
		}

		out.println("done: " + records + " tweets indexed");
		out.flush();
		out.close();
	}

	private SolrInputDocument toDocument( File f ) throws IOException
	{
		// read json file from disk
		StringBuffer buf = new StringBuffer();
		BufferedReader br = new BufferedReader( new FileReader(f) );
		for ( String line = null; (line=br.readLine()) != null; )
		{
			buf.append( line );
		}
		JSONObject json = new JSONObject( buf.toString() );
		SolrInputDocument doc = new SolrInputDocument();

		// basic info
		doc.addField("id",         json.get("id") );
		doc.addField("re_id",      json.get("re_id") );
		doc.addField("text",       json.get("text") );
		try
		{
			Date date = jsonFormat.parse(json.getString("date"));
			doc.addField("date",       utcFormat.format(date) );
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}

		// user info
		doc.addField("user_id",    json.get("user_id") );
		doc.addField("user_name",  json.get("user_name") );
		doc.addField("user_image", json.get("user_image") );

		// hashtags
		JSONArray tags = json.getJSONArray("tags");
		for ( int i = 0; i < tags.length(); i++ )
		{
			doc.addField("tag", tags.getString(i));
		}

		// media
		JSONArray media = json.getJSONArray("media");
		for ( int i = 0; i < media.length(); i++ )
		{
			doc.addField("media", media.getString(i));
		}

		return doc;
	}

	public static void main( String[] args ) throws Exception
	{
		SolrIndexer indexer = new SolrIndexer();
        Properties props = new Properties();
        props.load( new FileInputStream(args[0]) );
		indexer.init(props);

		if ( args.length > 1 && args[1].equals("reindex") )
		{
			// reindex all tweets
			indexer.index(null, new PrintWriter(System.out));
		}
		else
		{
			// index new tweets
			indexer.run();
		}
	}
}
