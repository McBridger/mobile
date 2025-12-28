import React, { useState } from "react";
import {
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { wordlist } from "@scure/bip39/wordlists/english.js";

interface Props {
  length: number;
  onSave: (phrase: string) => void;
}

export const MnemonicForm = ({ length, onSave }: Props) => {
  const [words, setWords] = useState<string[]>(Array(length).fill(""));

  const handleInputChange = (text: string, index: number) => {
    const newWords = [...words];
    newWords[index] = text.toLowerCase().trim();
    setWords(newWords);
  };

  const generateRandomPhrase = () => {
    const randomWords = Array.from({ length }, () => {
      const randomIndex = Math.floor(Math.random() * wordlist.length);
      return wordlist[randomIndex];
    });
    setWords(randomWords);
  };

  const isValid = (word: string) => word === "" || wordlist.includes(word);
  const isComplete = words.every(
    (word) => word.length > 0 && wordlist.includes(word)
  );

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Setup McBridge</Text>
      <Text style={styles.subtitle}>
        Enter a {length}-word phrase or generate a new one to start.
      </Text>

      <View style={styles.grid}>
        {words.map((word, index) => (
          <View key={index} style={styles.inputWrapper}>
            <Text style={styles.label}>Word #{index + 1}</Text>
            <TextInput
              testID={`mnemonic-input-${index}`}
              accessibilityLabel={`mnemonic-input-${index}`}
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

      <TouchableOpacity
        style={styles.generateButton}
        onPress={generateRandomPhrase}
      >
        <Text style={styles.generateButtonText}>Generate New Phrase</Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.button, !isComplete && styles.buttonDisabled]}
        onPress={() => onSave(words.join("-"))}
        disabled={!isComplete}
      >
        <Text style={styles.buttonText}>Start Magic Sync</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: "100%",
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
});
