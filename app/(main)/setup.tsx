import React, { useState, useEffect } from "react";
import {
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Alert,
} from "react-native";
import { useRouter } from "expo-router";
import ConnectorModule from "@/modules/connector";
import { wordlist } from "@scure/bip39/wordlists/english.js";
import { useAppConfig } from "@/hooks/useConfig";

export default function Setup() {
  const router = useRouter();
  const { extra } = useAppConfig();
  
  const [isReady, setIsReady] = useState(false);
  const [mnemonic, setMnemonic] = useState<string | null>(null);
  const [showMnemonic, setShowMnemonic] = useState(false);
  
  // Input state for new setup
  const [words, setWords] = useState<string[]>(Array(extra.MNEMONIC_LENGTH).fill(""));

  useEffect(() => {
    const ready = ConnectorModule.isReady();
    setIsReady(ready);
    if (ready) setMnemonic(ConnectorModule.getMnemonic());
  }, []);

  const handleInputChange = (text: string, index: number) => {
    const newWords = [...words];
    newWords[index] = text.toLowerCase().trim();
    setWords(newWords);
  };

  const generateRandomPhrase = () => {
    const randomWords = Array.from({ length: extra.MNEMONIC_LENGTH }, () => {
      const randomIndex = Math.floor(Math.random() * wordlist.length);
      return wordlist[randomIndex];
    });
    setWords(randomWords);
  };

  const isValid = (word: string) => word === "" || wordlist.includes(word);
  const isComplete = words.every((word) => word.length > 0 && wordlist.includes(word));

  const handleSave = async () => {
    if (!isComplete) {
      Alert.alert("Invalid Phrase", "Please ensure all words are from the standard list.");
      return;
    }
    
    const phrase = words.join("-");

    try {
      await ConnectorModule.setup(phrase, extra.ENCRYPTION_SALT);
      await ConnectorModule.start();
      router.replace("/");
    } catch (e) {
      Alert.alert("Error", "Failed to initialize secure storage.");
    }
  };

  const handleReset = () => {
    Alert.alert(
      "Reset Setup?",
      "This will permanently delete your pairing phrase from this device.",
      [
        { text: "Cancel", style: "cancel" },
        { 
          text: "Reset", 
          style: "destructive", 
          onPress: async () => {
            await ConnectorModule.reset();
            setIsReady(false);
            setMnemonic(null);
            setWords(Array(extra.MNEMONIC_LENGTH).fill(""));
          } 
        }
      ]
    );
  };

  if (isReady) {
    return (
      <View style={styles.container}>
        <ScrollView contentContainerStyle={styles.scrollContent}>
          <Text style={styles.title}>Your Mnemonic</Text>
          <Text style={styles.subtitle}>
            Use this phrase on other your device to establish a secure connection.
          </Text>

          <View style={styles.phraseCard}>
            <TouchableOpacity 
              style={styles.phraseContainer} 
              onPress={() => setShowMnemonic(!showMnemonic)}
              activeOpacity={0.7}
            >
              <Text style={[styles.phraseText, !showMnemonic && styles.phraseHidden]}>
                {showMnemonic ? mnemonic : "•••• •••• •••• ••••"}
              </Text>
              <Text style={styles.phraseHint}>
                {showMnemonic ? "Tap to hide" : "Tap to reveal"}
              </Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity style={styles.resetButton} onPress={handleReset}>
            <Text style={styles.resetButtonText}>Reset Setup</Text>
          </TouchableOpacity>
        </ScrollView>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === "ios" ? "padding" : "height"}
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>Setup McBridge</Text>
        <Text style={styles.subtitle}>
          Enter a {extra.MNEMONIC_LENGTH}-word phrase or generate a new one to start.
        </Text>

        <View style={styles.grid}>
          {words.map((word, index) => (
            <View key={index} style={styles.inputWrapper}>
              <Text style={styles.label}>Word #{index + 1}</Text>
              <TextInput
                style={[styles.input, !isValid(word) && styles.inputInvalid]}
                value={word}
                onChangeText={(text) => handleInputChange(text, index)}
                placeholder="---"
                autoCapitalize="none"
                autoCorrect={false}
              />
            </View>
          ))}
        </View>

        <TouchableOpacity style={styles.generateButton} onPress={generateRandomPhrase}>
          <Text style={styles.generateButtonText}>Generate New Phrase</Text>
        </TouchableOpacity>

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
  },
  inputInvalid: {
    borderColor: "#FF3B30",
    backgroundColor: "#FFF5F5",
  },
  generateButton: {
    padding: 12,
    marginBottom: 20,
  },
  generateButtonText: {
    color: "#007AFF",
    fontSize: 16,
    fontWeight: "600",
  },
  button: {
    backgroundColor: "#007AFF",
    width: "100%",
    padding: 18,
    borderRadius: 16,
    alignItems: "center",
    marginTop: 10,
  },
  buttonDisabled: {
    backgroundColor: "#ccc",
  },
  buttonText: {
    color: "#fff",
    fontSize: 18,
    fontWeight: "700",
  },
  phraseCard: {
    backgroundColor: "#fff",
    borderRadius: 16,
    padding: 20,
    width: "100%",
    borderWidth: 1,
    borderColor: "#e0e0e0",
  },
  phraseContainer: {
    backgroundColor: "#f9f9f9",
    borderRadius: 12,
    padding: 20,
    alignItems: "center",
    borderStyle: "dashed",
    borderWidth: 1,
    borderColor: "#ccc",
  },
  phraseText: {
    fontSize: 20,
    fontWeight: "700",
    color: "#1a1a1a",
    textAlign: "center",
  },
  phraseHidden: {
    color: "#ccc",
    letterSpacing: 4,
  },
  phraseHint: {
    fontSize: 12,
    color: "#007AFF",
    marginTop: 10,
    fontWeight: "600",
  },
  resetButton: {
    marginTop: 40,
    padding: 15,
  },
  resetButtonText: {
    color: "#FF3B30",
    fontSize: 16,
    fontWeight: "600",
  },
});