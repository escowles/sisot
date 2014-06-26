package org.ticklefish.sisot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;

/**
 * Base servlet class, implementing basic config handling.
 *
 * @author escowles
 * @since 2014-06-20
**/
public abstract class AbstractServlet extends HttpServlet
{
	protected Properties props;
	protected File confFile;
	protected SolrServer solr;

	public void init( ServletConfig cfg )
	{
		// load config file and get Solr URI
		String conf = cfg.getInitParameter("conf");
		if ( conf == null ) { conf = System.getProperty("sisot.conf"); }
		if ( conf == null ) { conf = "sisot.conf"; }

		confFile = new File(conf);
		System.out.println("using config file: " + confFile.getAbsolutePath());
		if ( confFile.exists() && confFile.canRead() )
		{
			try
			{
				props = new Properties();
				props.load( new FileInputStream(confFile) );
			}
			catch ( Exception ex )
			{
				ex.printStackTrace();
				props = null;
			}
		}
		init(props);
	}
	protected void init( Properties props )
	{
		if ( props != null )
		{
			solr = new HttpSolrServer( props.getProperty("solr") );
		}
	}

	protected void doPost( HttpServletRequest req, HttpServletResponse res )
	{
		// write config file
		props = new Properties();
		props.put("solr",          req.getParameter("solr")          );
		props.put("api.key",       req.getParameter("api.key")       );
		props.put("api.secret",    req.getParameter("api.secret")    );
		props.put("access.token",  req.getParameter("access.token")  );
		props.put("access.secret", req.getParameter("access.secret") );
		init(props);

		String error = null;
		try
		{
			FileOutputStream out = new FileOutputStream(confFile);
			props.store(out, "sisot configuration");
		}
		catch ( Exception ex )
		{
			error = ex.toString();
		}

		PrintWriter out = null;
		try
		{
			out = res.getWriter();
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			return;
		}

		out.println("<html><body>");
		if ( error == null )
		{
			out.println("<p>configuration saved successfully</p>");
		}
		else
		{
			out.println("<p>error saving configuration: " + error + "</p>");
		}
		out.println("</body></html>");
	}
}
