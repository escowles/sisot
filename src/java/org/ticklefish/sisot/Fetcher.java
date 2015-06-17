package org.ticklefish.sisot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import twitter4j.Paging;
import twitter4j.RateLimitStatus;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.HashtagEntity;
import twitter4j.ExtendedMediaEntity;
import twitter4j.URLEntity;

import org.json.JSONObject;

/**
 * Fetch tweet data.
 *
 * @author escowles
 * @since 2014-07-01
**/
public class Fetcher
{
	private static SimpleDateFormat dfmt = new SimpleDateFormat("MM/dd HH:mm");

	private static Twitter twitter( Properties props ) throws Exception
	{
		// load config
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

	private static String fetch( Twitter twitter, long id )
		throws TwitterException
	{
		try
		{
			Status tweet = twitter.showStatus(id);
			return json(tweet);
		}
		catch ( TwitterException ex )
		{
			if ( ex.exceededRateLimitation() )
			{
				throw ex;
			}
			else if ( ex.getStatusCode() == 404 )
			{
				System.err.println("Not Found: " + id);
			}
		}
		return null;
	}
	private static void fetch( Twitter twitter, File dir ) throws IOException
	{
		int limit = remaining( twitter, "statuses", "lookup" );
		if ( limit < 1 )
		{
			return;
		}

		int records = 0;
		int batches = 0;
		File[] files = dir.listFiles();
		long[] ids = new long[100];
		for ( int i = 0; i < files.length && batches < limit; i++ )
		{
			File f = files[i];
			if ( f.getName().endsWith(".id") )
			{
				String id = f.getName().replaceAll(".id","");
				File jsonFile = new File( dir, id + ".json" );
				if ( !jsonFile.exists() )
				{
					ids[records] = Long.parseLong(id);
					records++;
					if ( records == 100 )
					{
						fetchBatch(twitter, ids, dir);
						batches++;
						records = 0;
						ids = new long[100];
					}
				}
			}
		}
		if ( records > 0 )
		{
			fetchBatch(twitter, ids, dir);
		}
	}

	private static void fetchBatch( Twitter twitter, long[] ids, File dir )
	{
		try
		{
			ResponseList<Status> tweets = twitter.lookup(ids);
			for ( Status tweet : tweets )
			{
				String json = json(tweet);
				String id = String.valueOf(tweet.getId());
				File f = new File( dir, id + ".id" );
				if ( json != null )
				{
					System.out.println("id " + id);
					File jsonFile = new File( dir, id + ".json" );
					FileWriter fw = new FileWriter( jsonFile );
					fw.write( json );
					fw.close();
					f.delete();
				}
				else
				{
					System.out.println("Error: " + id);
					File errFile = new File( dir, id + ".err" );
					f.renameTo( errFile );
				}
			}
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
		catch ( IOException ex )
		{
			System.out.println( "Error writing file: " + ex.toString());
		}
	}

	private static String json( Status tweet )
	{
		JSONObject json = new JSONObject();
		json.put("id",         tweet.getId() );
		json.put("re_id",      tweet.getInReplyToStatusId() );
		json.put("date",       tweet.getCreatedAt() );
		json.put("user_id",    tweet.getUser().getScreenName() );
		json.put("user_name",  tweet.getUser().getName() );
		json.put("user_image", tweet.getUser().getProfileImageURL() );

		if ( tweet.isRetweet() )
		{
			// use original tweet's text so links aren't truncated
			System.out.println("RT " + tweet.getId());
			Status rt = tweet.getRetweetedStatus();
			json.put( "text", "RT @" + rt.getUser().getScreenName()
				+ ": " + expandedText(rt) );
		}
		else
		{
			json.put("text",       expandedText(tweet) );
		}

		// hashtags
        HashtagEntity[] tags = tweet.getHashtagEntities();
		List<String> tagList = new ArrayList<>();
        for ( int i = 0; i < tags.length; i++ )
        {
            tagList.add(tags[i].getText());
        }
		json.put( "tags", tagList );

        // media
        ExtendedMediaEntity[] media = tweet.getExtendedMediaEntities();
		List<String> mediaList = new ArrayList<>();
        for ( int i = 0; i < media.length; i++ )
        {
            if ( media[i].getType().equals("animated_gif") )
			{
            	mediaList.add(media[i].getMediaURL() + " " + media[i].getExpandedURL());
			}
			else
			{
            	mediaList.add(media[i].getMediaURL());
			}
        }
		json.put("media", mediaList );

		return json.toString();
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

	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		Twitter twitter = twitter( props );

		if ( args.length > 1 && args[1] != null )
		{
			try
			{
				System.out.println( fetch(twitter, Long.parseLong(args[1])) );
			}
			catch ( Exception ex )
			{
				ex.printStackTrace();
			}
		}
		else
		{
			File dir = new File( props.getProperty("data.dir") );
			fetch( twitter, dir );
		}
	}
}
