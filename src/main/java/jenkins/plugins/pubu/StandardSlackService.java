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

    private String host = "";
    private String teamDomain;
    private String token;
    private String[] roomIds;

    public StandardSlackService(String url) {
        super();
        this.host = url;
        this.teamDomain = url;
        this.token = url;
        this.roomIds = url.split("[,; ]+");
    }

    public boolean publish(String message) {
        return publish(message, "warning");
    }

    public boolean publish(JSONObject payload) {
        logger.info("Posting: to " + host + " using " + host);
        HttpClient client = getHttpClient();
        PostMethod post = new PostMethod(host);

        try {
            payload.put("_version", 2);

            post.addParameter("payload", payload.toString());
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
            return true;
        }

    }

    public boolean publish(String message, String color) {
        String url = host;
        logger.info("Posting: to " + host + " using " + url + ": " + message + " " + color);
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

            json.put("attachments", attachments);


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
            return true;
        }

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
