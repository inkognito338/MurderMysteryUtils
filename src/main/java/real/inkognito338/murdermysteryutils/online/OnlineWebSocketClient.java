package real.inkognito338.murdermysteryutils.online;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 20.07.2026
 */
public abstract class OnlineWebSocketClient extends WebSocketClient {

    public OnlineWebSocketClient(String url) {
        super(URI.create(url), new Draft_6455());
        try {
            this.setConnectionLostTimeout(30);
        } catch (NoSuchMethodError | Exception ignored) {
            // Игнорируем, если метод недоступен
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        // Will be overridden
    }

    @Override
    public void onMessage(String message) {
        // Will be overridden
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // Will be overridden
    }

    @Override
    public void onError(Exception ex) {
        // Will be overridden
    }
}