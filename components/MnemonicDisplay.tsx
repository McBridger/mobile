import { AppTheme } from "@/theme/CustomTheme";
import React, { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, Surface, Text, useTheme } from "react-native-paper";

interface Props {
  mnemonic: string | null;
  onReset: () => void;
}

export const MnemonicDisplay = ({ mnemonic, onReset }: Props) => {
  const [show, setShow] = useState(false);
  const theme = useTheme() as AppTheme;

  return (
    <View style={styles.container}>
      <Text
        variant="titleLarge"
        style={[styles.title, { color: theme.colors.onSurface }]}
      >
        Your Mnemonic phrase
      </Text>
      <Text
        variant="bodyLarge"
        style={[styles.subtitle, { color: theme.colors.onSurfaceVariant }]}
      >
        Use this phrase on your other devices to establish a secure connection.
      </Text>

      <Surface
        style={[
          styles.glassBox,
          {
            backgroundColor: theme.colors.surface,
            borderColor: theme.colors.outline,
          },
        ]}
        elevation={1}
      >
        <Text
          variant="titleLarge"
          style={[
            styles.placeholderDots,
            {
              color: theme.colors.onSurfaceVariant,
              opacity: show ? 1 : 0.3,
            },
          ]}
        >
          {show ? mnemonic : "•••• •••• •••• ••••"}
        </Text>
        <Button
          onPress={() => setShow(!show)}
          mode="contained"
          buttonColor={theme.colors.primaryMuted}
          textColor={theme.colors.primary}
          style={styles.btnReveal}
        >
          {show ? "Hide phrase" : "Reveal phrase"}
        </Button>
      </Surface>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: "100%",
    alignItems: "center",
    height: "100%",
  },
  title: {
    marginTop: 20,
    marginBottom: 12,
  },
  subtitle: {
    textAlign: "center",
    marginBottom: 24,
    paddingHorizontal: 10,
  },
  glassBox: {
    width: "100%",
    borderRadius: 24,
    padding: 40,
    paddingHorizontal: 20,
    alignItems: "center",
    borderWidth: 1,
    marginBottom: 24,
  },
  placeholderDots: {
    letterSpacing: 4,
    marginBottom: 24,
    textAlign: "center",
  },
  btnReveal: {
    borderRadius: 16,
    paddingHorizontal: 28,
    paddingVertical: 6,
  },
});
