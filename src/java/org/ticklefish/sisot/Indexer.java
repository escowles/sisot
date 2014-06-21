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
	private SimpleDateFormat df = new SimpleDateFormat(
		"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
	);
	Twitter twitter;
	public void init( ServletConfig cfg )
	{
		super.init(cfg);
		init(props);
		df.setTimeZone(TimeZone.getTimeZone("UTC"));

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

	public void run()
	{
		// make sure we're configured
		if ( twitter == null )
		{
			System.out.println("Not configured, will retry in 30 minutes");
			return;
		}

		// check rate limit status
		int limitRemaining = 0;
		try
		{
			RateLimitStatus limit = twitter.getRateLimitStatus("statuses")
				.get("/statuses/home_timeline");
			limitRemaining = limit.getRemaining();
			if ( limitRemaining < 1 )
			{
				Date d = new Date( limit.getResetTimeInSeconds() * 1000L );
				System.out.println(
					"Rate limit exceeded, retry after " + df.format(d)
				);
				return;
			}
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
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
			for ( int page = 1; limitRemaining-- > 0; page++ )
			{
				paging.setPage(page);
				List<Status> statuses = twitter.getHomeTimeline(paging);
				if ( statuses.size() == 0 ) { limitRemaining = 0; }
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
			System.out.println("indexed " + records + " tweets in " + dur
				+ " msec");
		}
		catch ( TwitterException ex )
		{
			if ( ex.exceededRateLimitation() )
			{
				Date d = new Date(
					ex.getRateLimitStatus().getResetTimeInSeconds() * 1000L
				);
				System.out.println(
					"Rate limit exceeded, retry after " + df.format(d)
				);
			}
			else
			{
				System.out.println( "Error indexing tweets: " + ex.toString() );
			}
		}
	}
	private SolrInputDocument toDocument( Status status )
	{
		SolrInputDocument doc = new SolrInputDocument();

		// basic info
		doc.addField("id",         status.getId() );
		doc.addField("re_id",      status.getInReplyToStatusId() );
		doc.addField("text",       status.getText() );
		doc.addField("date",       df.format(status.getCreatedAt()) );

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
}