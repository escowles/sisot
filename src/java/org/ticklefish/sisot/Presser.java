package org.ticklefish.sisot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

import java.util.Properties;

import redstone.xmlrpc.XmlRpcFault;

import net.bican.wordpress.Page;
import net.bican.wordpress.PageDefinition;
import net.bican.wordpress.Wordpress;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Post tweets to Wordpress
 *
 * @author escowles
 * @since 2014-07-03
**/
public class Presser
{
	private static void press( Properties props ) throws Exception
	{
		File dir = new File(props.getProperty("data.dir"));

		String username = props.getProperty("wp.user");
		String password = props.getProperty("wp.pass");
		String xmlrpcURL = props.getProperty("wp.url");
		Wordpress wp = new Wordpress( username, password, xmlrpcURL );

		int records = 0;
		File[] files = dir.listFiles();
		for ( int i = 0; i < files.length; i++ )
		{
			File f = files[i];
			if ( f.getName().endsWith(".json") )
			{
				String id = f.getName().replaceAll(".json","");
				records++;
				System.out.println( records + ": " + id );
				JSONObject tweet = parse( f );
				post( wp, tweet );

				// move file
				File wpFile = new File( dir, id + ".wp" );
				f.renameTo( wpFile );
			}
		}
		System.out.println("posted " + records + " tweets");
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
	private static void post( Wordpress wp, JSONObject tweet )
		throws Exception
	{
        Page p = new Page();
        p.setTitle( tweet.getString("date") );

		StringBuffer desc = new StringBuffer();
		desc.append("<table>\n<tr>\n");
        desc.append("<td><a href=\"http://twitter.com/" + tweet.getString("user_id") + "\">");
		desc.append("<img src=\"" + tweet.getString("user_image") + "\"></a></td>\n");
        desc.append("<td><a href=\"http://twitter.com/" + tweet.getString("user_id") + "\">");
		desc.append(tweet.getString("user_name") + "</a>:<br/>");
        desc.append(linkify(tweet.getString("text")) + "\n");

		// media
		JSONArray media = tweet.getJSONArray("media");
		if ( media.length() > 0 ) { desc.append("<br/>\n"); }
		for ( int i = 0; i < media.length(); i++ )
		{
			desc.append("<a href=\"" + media.getString(i) + "\">");
			desc.append("<img src=\"" + media.getString(i) + "\"");
			desc.append( " width=\"48\" height=\"48\"/></a>\n");
		}
        desc.append("</td></tr></table>");
        p.setDescription(desc.toString());

		// tags
		JSONArray tagArr = tweet.getJSONArray("tags");
		String tags = "";
		for ( int i = 0; i < tagArr.length(); i++ )
		{
			tags += tagArr.getString(i);
			if ( (i+1) < tagArr.length() ) { tags += ","; }
		}
        p.setMt_keywords(tags);

        String id = wp.newPost(p, true);
		System.out.println("posted: " + id);
	}
	private static String linkify( String s )
	{
		StringBuffer buf = new StringBuffer();
		String[] words = s.split("\\s+");
		for ( int i = 0; i < words.length; i++ )
		{
			if ( words[i].startsWith("@") || words[i].startsWith(".@") )
			{
				String link = "http://twitter.com/" + words[i].substring(words[i].indexOf("@")+1);
				buf.append( linkTo(link, words[i]) );
			}
			else if ( words[i].startsWith("#") )
			{
				String link = "tweet/tag/" + words[i].substring(1);
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
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		press( props );
	}
}
