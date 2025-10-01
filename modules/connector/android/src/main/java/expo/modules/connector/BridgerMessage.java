package expo.modules.connector;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import no.nordicsemi.android.ble.data.Data;
import android.util.Log;

public class BridgerMessage {
    public enum MessageType {
        CLIPBOARD,
        DEVICE_NAME
    }

    public static class Builder {
        private int type;
        private String payload;

        public Builder setType(MessageType type) {
            this.type = type.ordinal();
            return this;
        }

        public Builder setPayload(String payload) {
            this.payload = payload;
            return this;
        }

        public BridgerMessage build() {
            return new BridgerMessage(this);
        }
    }

    private static final String TAG = "Message";
    private static final Gson gson = new Gson();

    public static BridgerMessage parse(Data data) {
        Log.d(TAG, "Raw data received from peripheral: " + data.toString());

        String jsonString = data.getStringValue(0);
        if (jsonString == null) {
            Log.w(TAG, "Received data, but it could not be parsed as a String (JSON).");
            return null;
        }

        try {
            BridgerMessage receivedMessage = gson.fromJson(jsonString, BridgerMessage.class);
            Log.d(TAG, "Data received in Singleton (as JSON): Type=" + receivedMessage.getType() + ", Payload="
                    + receivedMessage.payload);
            return receivedMessage;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON message: " + e.getMessage());
            return null;
        }
    }

    @SerializedName("t")
    private final int type;

    @SerializedName("p")
    public final String payload;

    private BridgerMessage(Builder builder) {
        this.type = builder.type;
        this.payload = builder.payload;
    }

    public MessageType getType() {
        return MessageType.values()[type];
    }

    public String toJson() {
        return gson.toJson(this);
    }
}