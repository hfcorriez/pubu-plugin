package jenkins.plugins.pubu;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.Cookie;
import org.json.JSONObject;
import org.json.JSONArray;


import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import hudson.ProxyConfiguration;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;

public class StandardSlackService implements SlackService {

    private static final Logger logger = Logger.getLogger(StandardSlackService.class.getName());

    private String host = "app.com.t.proxylocal.com";
    private String teamDomain;
    private String token;
    private String[] roomIds;

    public StandardSlackService(String teamDomain, String token, String roomId) {
        super();
        this.teamDomain = teamDomain;
        this.token = token;
        this.roomIds = roomId.split("[,; ]+");
    }

    public boolean publish(String message) {
        return publish(message, "warning");
    }

    public boolean publish(String message, String color) {
        for (String roomId : roomIds) {
            //http://app.com.t.proxylocal.com/services/7yr6rv4zde2dt2llg9n8v3pbcz1
            String url = "http://" + host + "/services/" + token;
            logger.info("Posting: to " + roomId + " on " + teamDomain + " using " + url + ": " + message + " " + color);
            HttpClient client = getHttpClient();
            PostMethod post = new PostMethod(url);
            JSONObject json = new JSONObject();

            try {
                JSONObject field = new JSONObject();
                field.put("short", false);
                field.put("value", message);


                JSONArray fields = new JSONArray();
                fields.put(field);

                JSONObject attachment = new JSONObject();
                attachment.put("fallback", message);
                attachment.put("color", color);
                attachment.put("fields", fields);
                JSONArray attachments = new JSONArray();
                attachments.put(attachment);

                json.put("channel", roomId);
                json.put("attachments", attachments);

//                InetAddress ip = InetAddress.getLocalHost();
//                String hostname = ip.getHostName();
////                InetAddress addr = InetAddress.getByName(InetAddress.getLocalHost().getHostName());
//                String hostnameCanonical = ip.getCanonicalHostName();
//                String strDomainName = hostnameCanonical.substring(hostnameCanonical.indexOf(".") + 1);
//                json.put("host", strDomainName);
//                json.put("hostname", hostname);
//                json.put("hostnameCanonical", hostnameCanonical);


                json.put("host1", client.getHost());
                json.put("port", client.getPort());
                json.put("getHostConfiguration", client.getHostConfiguration());


                post.addParameter("payload", json.toString());
                post.getParams().setContentCharset("UTF-8");
                int responseCode = client.executeMethod(post);
                String response = post.getResponseBodyAsString();
                if (responseCode != HttpStatus.SC_OK) {
                    logger.log(Level.WARNING, "Pubu post may have failed. Response: " + response);
                    return false;
                }
                return true;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error posting to Pubu", e);
                return false;
            } finally {
                logger.info("Posting succeeded");
                post.releaseConnection();
            }
        }
        return false;
    }

    private HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        if (Jenkins.getInstance() != null) {
            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
            if (proxy != null) {
                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
                String username = proxy.getUserName();
                String password = proxy.getPassword();
                // Consider it to be passed if username specified. Sufficient?
                if (username != null && !"".equals(username.trim())) {
                    logger.info("Using proxy authentication (user=" + username + ")");
                    // http://hc.apache.org/httpclient-3.x/authentication.html#Proxy_Authentication
                    // and
                    // http://svn.apache.org/viewvc/httpcomponents/oac.hc3x/trunk/src/examples/BasicAuthenticationExample.java?view=markup
                    client.getState().setProxyCredentials(AuthScope.ANY,
                            new UsernamePasswordCredentials(username, password));
                }
            }
        }
        return client;
    }

    void setHost(String host) {
        this.host = host;
    }
}
