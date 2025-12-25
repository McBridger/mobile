import React, { useState } from "react";
import {
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from "react-native";
import { useRouter } from "expo-router";
import ConnectorModule from "@/modules/connector";

export default function Setup() {
  const router = useRouter();
  const [words, setWords] = useState<string[]>(Array(6).fill(""));

  const handleInputChange = (text: string, index: number) => {
    const newWords = [...words];
    newWords[index] = text.toLowerCase().trim();
    setWords(newWords);
  };

  const isComplete = words.every((word) => word.length > 0);

  const handleSave = async () => {
    if (!isComplete) return;
    
    // Join words into a single mnemonic phrase
    const mnemonic = words.join(" ");
    
    console.log("[Setup] Saving mnemonic:", mnemonic);
    
    // TODO: In the future, we will pass this to a proper setup method
    // For now, it will just verify that the native side is ready
    if (ConnectorModule.isReady()) {
      router.replace("/connection");
    } else {
      // If not ready (first time), we'll trigger discovery
      await ConnectorModule.startDiscovery();
      router.replace("/connection");
    }
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === "ios" ? "padding" : "height"}
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>Welcome to McBridge</Text>
        <Text style={styles.subtitle}>
          Enter your 6-word mnemonic phrase to pair with your Mac.
        </Text>

        <View style={styles.grid}>
          {words.map((word, index) => (
            <View key={index} style={styles.inputWrapper}>
              <Text style={styles.label}>Word #{index + 1}</Text>
              <TextInput
                style={styles.input}
                value={word}
                onChangeText={(text) => handleInputChange(text, index)}
                placeholder="---"
                autoCapitalize="none"
                autoCorrect={false}
              />
            </View>
          ))}
        </View>

        <TouchableOpacity
          style={[styles.button, !isComplete && styles.buttonDisabled]}
          onPress={handleSave}
          disabled={!isComplete}
        >
          <Text style={styles.buttonText}>Start Magic Sync</Text>
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f8f9fa",
  },
  scrollContent: {
    padding: 24,
    alignItems: "center",
  },
  title: {
    fontSize: 28,
    fontWeight: "800",
    color: "#1a1a1a",
    marginTop: 40,
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: "#666",
    textAlign: "center",
    marginBottom: 40,
    lineHeight: 22,
  },
  grid: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    width: "100%",
  },
  inputWrapper: {
    width: "48%",
    marginBottom: 20,
  },
  label: {
    fontSize: 12,
    fontWeight: "600",
    color: "#999",
    marginBottom: 6,
    textTransform: "uppercase",
  },
  input: {
    backgroundColor: "#fff",
    borderWidth: 1,
    borderColor: "#e0e0e0",
    borderRadius: 12,
    padding: 14,
    fontSize: 16,
    color: "#1a1a1a",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  button: {
    backgroundColor: "#007AFF",
    width: "100%",
    padding: 18,
    borderRadius: 16,
    alignItems: "center",
    marginTop: 20,
    shadowColor: "#007AFF",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 4,
  },
  buttonDisabled: {
    backgroundColor: "#ccc",
    shadowOpacity: 0,
  },
  buttonText: {
    color: "#fff",
    fontSize: 18,
    fontWeight: "700",
  },
});
