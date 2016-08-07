package jenkins.plugins.pubu;
import org.json.JSONObject;

public interface SlackService {
    boolean publish(String message);

    boolean publish(String message, String color);

    boolean publish(JSONObject payload);
}
