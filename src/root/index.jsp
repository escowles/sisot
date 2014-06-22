<%@ page import="
  org.json.JSONArray,
  org.json.JSONObject,
  org.apache.http.HttpResponse,
  org.apache.http.client.methods.HttpGet,
  org.apache.http.impl.client.DefaultHttpClient,
  org.apache.commons.io.IOUtils" %><%!

// XXX can we get the last-modified header?

private static String linkify( String s )
{
  StringBuffer buf = new StringBuffer();
  String[] words = s.split("\\s+");
  for ( int i = 0; i < words.length; i++ )
  {
    if ( words[i].startsWith("@") || words[i].startsWith(".@") )
    {
      String link = "?user=" + words[i].substring(words[i].indexOf("@")+1);
      buf.append( linkTo(link, words[i]) );
    }
    else if ( words[i].startsWith("#") )
    {
      String link = "?tag=" + words[i].substring(1);
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
%>
<html>
  <head>
    <title>sisot</title>
    <link rel="stylesheet" type="text/css" href="sisot.css"></link>
  </head>
  <body>
<%
  // set character encoding
  response.setCharacterEncoding("UTF-8");

  boolean debug = request.getParameter("debug") != null;
  String q = request.getParameter("q");
  if ( q == null ) { q = ""; }

  String re = request.getParameter("re");
  if ( re != null && !re.equals("") )
  { %>
    <h1>conversation</h1>
  <% }

  String tag = request.getParameter("tag");
  if ( tag != null && !tag.equals("") )
  { %>
    <h1>tag: <%= tag %>
      <a href="http://twitter.com/hashtag/<%=tag%>">
        <img src="twitter.png"/>
      </a>
    </h1>
  <% }

  String user = request.getParameter("user");
  if ( user != null && !user.equals("") )
  { %>
    <h1>user: <%=user%>
      <a href="http://twitter.com/<%=user%>">
        <img src="twitter.png"/>
      </a>
    </h1>
  <% }

  String url = request.getRequestURL().toString();
  if ( url.endsWith("/index.jsp") )
  {
    url = url.substring(0, url.indexOf("/index.jsp") );
  }
  url += "/searchJSON";
  if ( request.getQueryString() != null )
  {
    url += "?" + request.getQueryString();
  }
  System.out.println("fetching URL: " + url);

  DefaultHttpClient client = new DefaultHttpClient();
  HttpResponse resp = client.execute( new HttpGet(url) );
  String content = IOUtils.toString( resp.getEntity().getContent() );

  // parse and display...
  JSONObject json = new JSONObject( content );

  // header
  JSONObject pager = json.getJSONObject("pager");
  String prev = pager.optString("prev");
  String next = pager.optString("next");
%>
<table id="header">
  <tr>
    <td id="search">
      <form>
        <input name="q" value="<%=q%>"/>
        <input type="submit" value="search"/>
      </form>
    </td>
    <td id="pager">
      <% if ( !prev.equals("") ) { %><a href="<%=prev%>">prev</a><% } %>
      <%= pager.optString("current") %>
      <% if ( !next.equals("") ) { %><a href="<%=next%>">next</a><% } %>
    </td>
  </tr>
</table>
<table>
<%
  JSONArray tweets = json.getJSONArray("results");
  for ( int i = 0; i < tweets.length(); i++ )
  {
    JSONObject t = tweets.getJSONObject(i); %>
    <tr>
      <td>
        <a href="?user=<%= t.getString("user_id") %>">
          <img src="<%= t.getString("user_image") %>"/>
        </a>
      </td>
      <td>
        <%= t.getString("date") %>
        <a href="?user=<%= t.getString("user_id") %>">
          <%= t.getString("user_name") %></a>:<br/>
        <%= linkify(t.getString("text")) %>
      </td>
      <td>
      <%
        String replyTo = t.optString("re_id");
        if ( replyTo != null && !replyTo.equals("") && !replyTo.equals("-1") )
        { %>
          <a href="?re=<%= replyTo %>">
            <img src="conversation.png">
          </a>
        <% }

        JSONArray media = t.optJSONArray("media");
        for ( int m = 0; media != null && m < media.length(); m++ )
        { %>
          <a href="<%= media.getString(m) %>"/>
            <img width="48" height="48" src="<%= media.getString(m) %>"/>
          </a>
        <% }
      %>
      </td>
    </tr>
    <% if ( debug ) { %><tr><td colspan="3"><%= t.toString() %></td></tr><% } %>
  <%
  }
%>
  </body>
</html>
