package org.ticklefish.sisot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.common.SolrInputDocument;

/**
 * Index tweets in Solr.
 *
 * @author escowles
 * @since 2015-06-14
**/
public class Indexer
{
	private static SimpleDateFormat fmt1 = new SimpleDateFormat(
		"EEE MMM dd HH:mm:ss z yyyy"
	);
	private static SimpleDateFormat fmt2 = new SimpleDateFormat(
		"yyyy-MM-dd'T'HH:mm:ss'Z'"
	);

	private static void press( Properties props ) throws Exception
	{
		File dir = new File(props.getProperty("data.dir"));

		String solrBase = props.getProperty("solr.base");
		int queue = 20;
		int threads = 3;
		SolrServer solr = new ConcurrentUpdateSolrServer(solrBase, queue, threads);

		int records = 0;
		File[] files = dir.listFiles();
		for ( int i = 0; i < files.length; i++ )
		{
			File f = files[i];
			if ( f.getName().endsWith(".json") )
			{
				String id = f.getName().replaceAll(".json","");
				records++;

				try
				{
					System.out.println( records + ": " + id );
					JSONObject tweet = parse( f );

					index( solr, tweet );

					// move file
					File wpFile = new File( dir, id + ".wp" );
					f.renameTo( wpFile );
				}
				catch ( Exception ex )
				{
					System.out.println("Exception: " + ex.toString());
					ex.printStackTrace();
					File errFile = new File( dir, id + ".err" );
					f.renameTo( errFile );
				}
			}
		}
	}
	private static JSONObject parse( File f ) throws Exception
	{
		StringBuffer buf = new StringBuffer();
		BufferedReader in = new BufferedReader( new FileReader(f) );
		for ( String line = null; (line=in.readLine()) != null; )
		{
			buf.append(line);
		}
		return new JSONObject( buf.toString() );
	}
	private static void index( SolrServer solr, JSONObject tweet )
		throws Exception
	{
try {
		SolrInputDocument doc = new SolrInputDocument();
		long id = tweet.getLong("id");
		doc.addField("id", String.valueOf(id));
		Date d = fmt1.parse( tweet.getString("date") );
		doc.addField("date", fmt2.format(d));

        doc.addField("user_id", tweet.getString("user_id"));
		doc.addField("user_image", tweet.getString("user_image"));
		doc.addField("user_name", tweet.getString("user_name"));
        doc.addField("tweet_text", linkify(tweet.getString("text")));

		// media
		JSONArray media = tweet.getJSONArray("media");
		for ( int i = 0; i < media.length(); i++ )
		{
			doc.addField("media", media.getString(i));
		}

		// tags
		JSONArray tagArr = tweet.getJSONArray("tags");
		for ( int i = 0; i < tagArr.length(); i++ )
		{
			doc.addField("tags", tagArr.getString(i));
		}

		solr.add(doc);
} catch ( Exception ex ) { ex.printStackTrace(); }
	}
	private static String linkify( String s )
	{
		StringBuffer buf = new StringBuffer();
		String[] words = s.split("\\s+");
		for ( int i = 0; i < words.length; i++ )
		{
			if ( words[i].startsWith("@") || words[i].startsWith(".@") )
			{
				String link = "?f[user_id][]=" + words[i].substring(words[i].indexOf("@")+1);
				buf.append( linkTo(link, words[i]) );
			}
			else if ( words[i].startsWith("#") )
			{
				String link = "?f[tags][]=" + words[i].substring(1);
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

	public static void main( String[] args ) throws Exception
	{
		//fmt2.setTimeZone( TimeZone.getTimeZone("America/New_York") );
		fmt2.setTimeZone( TimeZone.getTimeZone("UTC") );

		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		press( props );
	}
}
