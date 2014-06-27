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
 * Config servlet.
 *
 * @author escowles
 * @since 2014-06-26
**/
public class Config extends AbstractServlet
{
	public void doGet( HttpServletRequest req, HttpServletResponse res )
	{
		// show config form
		PrintWriter out = null;
		try
		{
			out = res.getWriter();
		}
		catch ( IOException ex )
		{
			ex.printStackTrace();
			return;
		}

		out.println("<html>");
		out.println("  <head>");
		out.println("    <title>sisot - config</title>");
		out.println("    <link rel=\"stylesheet\" href=\"sisot.css\"></link>");
		out.println("  </head>");
		out.println("  <body>");
		out.println("    <h1>sisot config</h1>");
		out.println("    <form action=\"\" method=\"POST\">");

		field( "solr",          "solr",          out );
		field( "api key",       "api.key",       out );
		field( "api secret",    "api.secret",    out );
		field( "access token",  "access.token",  out );
		field( "access secret", "access.secret", out );

		out.println("      <p><span class=\"label\"></span>");
		out.println("         <input type=\"submit\" value=\"save\"/></p>");
		out.println("    </form>");
		out.println("  </body>");
		out.println("</html>");
	}

	private void field( String label, String name, PrintWriter out )
	{
		String value = props.getProperty(name,"");
		out.println("      <p><span class=\"label\">" + label + "</span>"
			+ "<input type=\"text\" name=\"" + name + "\" value=\"" + value
			+ "\"/></p>");
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
