package expo.modules.connector;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class BridgerHistory {

    private static final String TAG = "BridgerHistory";
    private static volatile BridgerHistory INSTANCE;

    private final WeakReference<Context> contextRef;
    private final Gson gson = new Gson();
    private File historyFile;
    private final ConcurrentLinkedQueue<String> historyQueue = new ConcurrentLinkedQueue<>();

    private BridgerHistory(Context context) {
        this.contextRef = new WeakReference<>(context);
        loadHistoryFromFile();
    }

    public static BridgerHistory getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (BridgerHistory.class) {
                if (INSTANCE == null) {
                    INSTANCE = new BridgerHistory(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    private File getHistoryFile() {
        if (historyFile != null) return historyFile;

        Context context = contextRef.get();
        if (context == null) throw new IllegalStateException("Context is null, cannot create history file.");

        historyFile = new File(context.getFilesDir(), "bridger_history.json");
        return historyFile;
    }

    public void add(BridgerMessage message) {
        historyQueue.add(message.toJson());
        saveHistoryToFile();
        Log.d(TAG, "Added message to history.");
    }

    public List<Bundle> retrieve() {
        return historyQueue.stream()
                .map(messageJson -> {
                    try {
                        BridgerMessage bridgerMessage = gson.fromJson(messageJson, BridgerMessage.class);
                        return bridgerMessage.toBundle();
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing stored message JSON: " + e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void clear() {
        historyQueue.clear();
        File file = getHistoryFile();
        if (!file.exists()) return;

        if (file.delete())  Log.d(TAG, "Bridger history file cleared.");
    }

    private void loadHistoryFromFile() {
        File file = getHistoryFile();
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            java.lang.reflect.Type type = new TypeToken<ConcurrentLinkedQueue<String>>() {}.getType();
            ConcurrentLinkedQueue<String> loadedQueue = gson.fromJson(reader, type);
            if (loadedQueue != null) {
                historyQueue.addAll(loadedQueue);
            }
            Log.d(TAG, "Bridger history loaded from file. Total entries: " + historyQueue.size());
        } catch (IOException e) {
            Log.e(TAG, "Error loading history from file: " + e.getMessage());
            // Optionally, delete corrupted file
            file.delete();
        }
    }

    private void saveHistoryToFile() {
        try (FileWriter writer = new FileWriter(getHistoryFile())) {
            gson.toJson(historyQueue, writer);
            Log.d(TAG, "Bridger history saved to file.");
        } catch (IOException e) {
            Log.e(TAG, "Error saving history to file: " + e.getMessage());
        }
    }

}