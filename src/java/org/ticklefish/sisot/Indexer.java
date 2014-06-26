package org.ticklefish.sisot;

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

import twitter4j.HashtagEntity;
import twitter4j.MediaEntity;
import twitter4j.Paging;
import twitter4j.RateLimitStatus;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Sisot indexer.
 *
 * @author escowles
 * @since 2014-06-20
**/
public class Indexer extends AbstractServlet implements Runnable
{
	private SimpleDateFormat utcFormat = new SimpleDateFormat(
		"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
	);
	private SimpleDateFormat localFormat = new SimpleDateFormat("MM/dd HH:mm");
	Twitter twitter;
	public void init( ServletConfig cfg )
	{
		super.init(cfg);
		init(props);
		utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

		// schedule execution
		ScheduledExecutorService exec = new ScheduledThreadPoolExecutor(1);
		exec.scheduleWithFixedDelay(this, 0, 30, TimeUnit.MINUTES);
	}
	protected void init( Properties props )
	{
		super.init(props);
		if ( props != null )
		{
			String apiKey    = props.getProperty("api.key");
			String apiSecret = props.getProperty("api.secret");
			String accToken  = props.getProperty("access.token");
			String accSecret = props.getProperty("access.secret");

			// setup credentials
			ConfigurationBuilder builder = new ConfigurationBuilder();
			builder.setOAuthConsumerKey(apiKey);
			builder.setOAuthConsumerSecret(apiSecret);
			Configuration config = builder.build();
			AccessToken token = new AccessToken( accToken, accSecret );

			// get twitter client
			TwitterFactory factory = new TwitterFactory(config);
			twitter = factory.getInstance(token);
		}
	}

	public void doGet( HttpServletRequest req, HttpServletResponse res )
	{
		if ( solr == null )
		{
			res.sendRedirect("/config.html");
			return;
		}

		PrintWriter out = null;
		try
		{
			res.setContentType("text/plain");
			out = res.getWriter();
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			return;
		}

		int skip = 0;
		String skipStr = req.getParameter("skip");
		if ( skipStr != null && !skipStr.trim().equals("") )
		{
			skip = Integer.parseInt(skipStr);
			out.println("skip set to " + skip);
		}
		int max = skip + 1;
		int batch = 50;
		int records = 0;
		out.println("reindexing, starting at " + skip);
		out.flush();
		int limit = remaining( "statuses", "show/:id", out );

		try
		{
			while ( skip < max && records < limit )
			{
				// get a page of records
				long start = System.currentTimeMillis();
				SolrQuery query = new SolrQuery();
				query.setStart( skip );
				query.setRows( batch );
				query.addSortField( "id", SolrQuery.ORDER.asc );
				SolrDocumentList docs = solr.query(query).getResults();
	
				// update paging
				max = (int)docs.getNumFound();
				skip += batch;
	
				// reindex records
				for ( int i = 0; i < docs.size() && records < limit; i++ )
				{
					String id = null;
					try
					{
						id = (String)docs.get(i).getFirstValue("id");
						Status tweet = twitter.showStatus(Long.parseLong(id));
						solr.add( toDocument(tweet) );
						records++;
					}
					catch ( TwitterException ex )
					{
						if ( ex.exceededRateLimitation() )
						{
							throw ex;
						}
						out.println(
							"error indexing " + id + ": " + ex.toString()
						);
						ex.printStackTrace();
					}
				}

				long dur = System.currentTimeMillis() - start;
				out.println("indexed " + batch + " tweets in " + dur + " msec");
				out.flush();
			}
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			out.println("error indexing: " + ex.toString());
		}

		// if we finished, reset skip
		if ( records < limit )
		{
			skip = 0;
			out.println("rate limit remaining: " + (limit - records));
		}
		else
		{
			out.println(
				"rate limit reached, will restart at " + skip + " of " + max
			);
		}

		out.println("done: " + records + " tweets indexed");
		out.flush();
		out.close();
	}

	public void run()
	{
		// make sure we're configured
		if ( twitter == null )
		{
			System.out.println("Not configured, will retry in 30 minutes");
			return;
		}

		// check rate limit status
		int limit = remaining(
			"statuses", "home_timeline", new PrintWriter(System.out)
		);
		if ( limit < 1 )
		{
			return;
		}

		// get last tweet id
		long last = 0L;
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
				last = Long.parseLong(
					(String)results.get(0).getFirstValue("id")
				);
			}
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}

		// start indexing
		try
		{
			User user = twitter.verifyCredentials();
			Paging paging = new Paging(1,100);
			if ( last != 0L )
			{
				paging.setSinceId(last);
			}
			int records = 0;
			long start = System.currentTimeMillis();
			for ( int page = 1; limit-- > 0; page++ )
			{
				paging.setPage(page);
				List<Status> statuses = twitter.getHomeTimeline(paging);
				if ( statuses.size() == 0 ) { limit = 0; }
				for (Status status : statuses)
				{
					try
					{
						solr.add( toDocument(status) );
						records++;
					}
					catch ( Exception ex )
					{
						ex.printStackTrace();
					}
				}
			}
			long dur = System.currentTimeMillis() - start;
			System.out.println("indexed " + records + " tweets in " + dur + " msec");
		}
		catch ( TwitterException ex )
		{
			if ( ex.exceededRateLimitation() )
			{
				Date d = new Date(
					ex.getRateLimitStatus().getResetTimeInSeconds() * 1000L
				);
				System.out.println(
					"Rate limit exceeded, retry after " + localFormat.format(d)
				);
			}
			else
			{
				System.out.println( "Error indexing tweets: " + ex.toString() );
			}
		}
	}

	private int remaining( String group, String item, PrintWriter out )
	{
		// check rate limit status
		int limit = 0;
		try
		{
			RateLimitStatus limitStatus = twitter.getRateLimitStatus(group)
				.get("/" + group + "/" + item );
			limit = limitStatus.getRemaining();
			if ( limit < 1 )
			{
				Date d = new Date(limitStatus.getResetTimeInSeconds() * 1000L);
				out.println("Rate limit exceeded, retry after " + localFormat.format(d));
			}
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			limit = -1;
		}

		return limit;
	}
	private SolrInputDocument toDocument( Status status )
	{
		SolrInputDocument doc = new SolrInputDocument();

		// basic info
		doc.addField("id",         status.getId() );
		doc.addField("re_id",      status.getInReplyToStatusId() );
		doc.addField("text",       expandedText(status) );
		doc.addField("date",       utcFormat.format(status.getCreatedAt()) );

		// user info
		doc.addField("user_id",    status.getUser().getScreenName() );
		doc.addField("user_name",  status.getUser().getName() );
		doc.addField("user_image", status.getUser().getProfileImageURL() );

		// hashtags
		HashtagEntity[] tags = status.getHashtagEntities();
		for ( int i = 0; i < tags.length; i++ )
		{
			doc.addField("tag", tags[i].getText());
		}

		// media
		MediaEntity[] media = status.getMediaEntities();
		for ( int i = 0; i < media.length; i++ )
		{
			doc.addField("media", media[i].getMediaURL());
		}

		return doc;
	}
	private static String expandedText( Status status )
	{
		String s = status.getText();
		URLEntity[] urls = status.getURLEntities();
		for ( int i = 0; i < urls.length; i++ )
		{
			URLEntity url = urls[i];
			s = s.replace( url.getURL(), url.getExpandedURL() );
		}
		return s;
	}
}
