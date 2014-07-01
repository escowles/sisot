package org.ticklefish.sisot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

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
 * List tweets in a user's timeline
 *
 * @author escowles
 * @since 2014-07-01
**/
public class Lister
{
	private static SimpleDateFormat dfmt = new SimpleDateFormat("MM/dd HH:mm");

	private static Twitter twitter( File configFile ) throws Exception
	{
		// load config
		Properties props = new Properties();
		props.load( new FileInputStream(configFile) );
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
		return factory.getInstance(token);
	}

	private static int remaining( Twitter twitter, String group, String item )
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
				System.out.println("Rate limit exceeded, retry after "
					+ dfmt.format(d));
			}
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			limit = -1;
		}

		return limit;
	}

	private static void list( Twitter twitter, File dir ) throws IOException
	{
		try
		{
			int limit = remaining( twitter, "statuses", "home_timeline" );
			if ( limit < 1 )
			{
				return;
			}

			Paging paging = new Paging(1,100);
			int records = 0;
			long start = System.currentTimeMillis();
			for ( int page = 1; limit-- > 0; page++ )
			{
				paging.setPage(page);
				List<Status> statuses = twitter.getHomeTimeline(paging);
				if ( statuses.size() == 0 ) { limit = 0; }
				for (Status status : statuses)
				{
					long id = status.getId();
					File idFile = new File( dir, id + ".id" );
					File jsonFile = new File( dir, id + ".json" );
					if ( !idFile.exists() && !jsonFile.exists() )
					{
						records++;
						System.out.println( records + ": " + id);
						FileWriter fw = new FileWriter(idFile);
						fw.write( String.valueOf(id) );
						fw.close();
					}
				}
			}
			long dur = System.currentTimeMillis() - start;
		}
		catch ( TwitterException ex )
		{
			if ( ex.exceededRateLimitation() )
			{
				Date d = new Date(
					ex.getRateLimitStatus().getResetTimeInSeconds() * 1000L
				);
				System.out.println(
					"Rate limit exceeded, retry after " + dfmt.format(d)
				);
			}
			else
			{
				System.out.println( "Error listing tweets: " + ex.toString() );
			}
		}
	}

	public static void main( String[] args ) throws Exception
	{
		Twitter twitter = twitter( new File(args[0]) );
		File dir = new File(args[1]);
		list( twitter, dir );
	}
}
