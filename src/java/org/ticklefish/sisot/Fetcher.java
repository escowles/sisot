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
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.HashtagEntity;
import twitter4j.MediaEntity;
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

	private static void fetch( Twitter twitter, File dir ) throws IOException
	{
		try
		{
			int limit = remaining( twitter, "statuses", "show/:id" );
			if ( limit < 1 )
			{
				return;
			}

			int records = 0;
			File[] files = dir.listFiles();
			for ( int i = 0; i < files.length && records < limit; i++ )
			{
				File f = files[i];
				if ( f.getName().endsWith(".id") )
				{
					String id = f.getName().replaceAll(".id","");
					File jsonFile = new File( dir, id + ".json" );
					if ( !jsonFile.exists() )
					{
						try
						{
							records++;
							System.out.println( records + ": " + id );
							Status tweet = twitter.showStatus(
								Long.parseLong(id)
							);
							FileWriter fw = new FileWriter( jsonFile );
							fw.write( json(tweet) );
							fw.close();
							f.delete();
						}
						catch ( TwitterException inner )
						{
							if ( inner.exceededRateLimitation() )
							{
								throw inner;
							}
							else
							{
								inner.printStackTrace();
							}
						}
					}
				}
			}
			if ( records <= limit )
			{
				System.out.println("rate limit reached");
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
	}

	private static String json( Status tweet )
	{
		JSONObject json = new JSONObject();
		json.put("id",         tweet.getId() );
		json.put("re_id",      tweet.getInReplyToStatusId() );
		json.put("text",       expandedText(tweet) );
		json.put("date",       tweet.getCreatedAt() );
		json.put("user_id",    tweet.getUser().getScreenName() );
		json.put("user_name",  tweet.getUser().getName() );
		json.put("user_image", tweet.getUser().getProfileImageURL() );

		// hashtags
        HashtagEntity[] tags = tweet.getHashtagEntities();
		List<String> tagList = new ArrayList<>();
        for ( int i = 0; i < tags.length; i++ )
        {
            tagList.add(tags[i].getText());
        }
		json.put( "tags", tagList );

        // media
        MediaEntity[] media = tweet.getMediaEntities();
		List<String> mediaList = new ArrayList<>();
        for ( int i = 0; i < media.length; i++ )
        {
            mediaList.add(media[i].getMediaURL());
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
		Twitter twitter = twitter( new File(args[0]) );
		File dir = new File(args[1]);
		fetch( twitter, dir );
	}
}
