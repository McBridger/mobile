import { AppTheme } from "@/theme/CustomTheme";
import { wordlist } from "@scure/bip39/wordlists/english.js";
import React from "react";
import {
  StyleSheet,
  TextInput,
  View,
} from "react-native";
import { Button, Text, useTheme } from "react-native-paper";

interface Props {
  words: string[];
  onWordsChange: (words: string[]) => void;
  length: number;
}

export const MnemonicForm = ({ words, onWordsChange, length }: Props) => {
  const theme = useTheme() as AppTheme;

  const handleInputChange = (text: string, index: number) => {
    const newWords = [...words];
    newWords[index] = text.toLowerCase().trim();
    onWordsChange(newWords);
  };

  const generateRandomPhrase = () => {
    const randomWords = Array.from({ length }, () => {
      const randomIndex = Math.floor(Math.random() * wordlist.length);
      return wordlist[randomIndex];
    });
    onWordsChange(randomWords);
  };

  const isValid = (word: string) => word === "" || wordlist.includes(word);

  return (
    <View style={styles.container}>
      <Text 
        variant="titleLarge" 
        style={[styles.title, { color: theme.colors.onSurface }]}
      >
        Setup McBridge
      </Text>
      <Text 
        variant="bodyLarge" 
        style={[styles.subtitle, { color: theme.colors.onSurfaceVariant }]}
      >
        Enter a {length}-word phrase or generate a new one to start.
      </Text>

      <View style={styles.grid}>
        {words.map((word, index) => (
          <View key={index} style={styles.inputWrapper}>
            <Text
              variant="labelMedium"
              style={[styles.label, { color: theme.colors.onSurfaceVariant }]}
            >
              #{index + 1}
            </Text>
            <TextInput
              style={[
                styles.input,
                {
                  backgroundColor: theme.colors.surfaceVariant,
                  borderColor: theme.colors.outlineVariant,
                  color: theme.colors.onSurface,
                },
                !isValid(word) && {
                  borderColor: theme.colors.error,
                  backgroundColor: theme.colors.errorMuted,
                },
              ]}
              value={word}
              onChangeText={(text) => handleInputChange(text, index)}
              placeholder="---"
              placeholderTextColor={theme.colors.placeholder}
              autoCapitalize="none"
              autoCorrect={false}
            />
          </View>
        ))}
      </View>

      <Button
        mode="outlined"
        onPress={generateRandomPhrase}
        style={styles.generateButton}
        buttonColor={theme.colors.primaryMuted}
        textColor={theme.colors.primary}
        labelStyle={styles.generateButtonText}
      >
        Generate new phrase
      </Button>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: "100%",
    alignItems: "center",
  },
  title: {
    marginTop: 20,
    marginBottom: 8,
  },
  subtitle: {
    textAlign: "center",
    marginBottom: 30,
  },
  grid: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    width: "100%",
  },
  inputWrapper: {
    width: "31%",
    marginBottom: 16,
  },
  label: {
    marginBottom: 4,
    textTransform: "uppercase",
  },
  input: {
    borderWidth: 1,
    borderRadius: 12,
    padding: 12,
    fontSize: 14,
  },
  generateButton: {
    marginTop: 24,
    borderRadius: 16,
    paddingVertical: 6,
    paddingHorizontal: 28,
  },
  generateButtonText: {
    fontSize: 15,
    fontWeight: "bold",
  },
});
