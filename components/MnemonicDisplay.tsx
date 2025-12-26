import React, { useState } from "react";
import { StyleSheet, Text, TouchableOpacity, View } from "react-native";

interface Props {
  mnemonic: string | null;
  onReset: () => void;
}

export const MnemonicDisplay = ({ mnemonic, onReset }: Props) => {
  const [show, setShow] = useState(false);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Your Mnemonic</Text>
      <Text style={styles.subtitle}>
        Use this phrase on your other devices to establish a secure connection.
      </Text>

      <View style={styles.phraseCard}>
        <TouchableOpacity
          style={styles.phraseContainer}
          onPress={() => setShow(!show)}
          activeOpacity={0.7}
        >
          <Text style={[styles.phraseText, !show && styles.phraseHidden]}>
            {show ? mnemonic : "•••• •••• •••• ••••"}
          </Text>
          <Text style={styles.phraseHint}>
            {show ? "Tap to hide" : "Tap to reveal"}
          </Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={styles.resetButton} onPress={onReset}>
        <Text style={styles.resetButtonText}>Reset Setup</Text>
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
