import { MnemonicDisplay } from "@/components/MnemonicDisplay";
import { MnemonicForm } from "@/components/MnemonicForm";
import { useAppConfig } from "@/hooks/useConfig";
import ConnectorModule, { STATUS, useConnector } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import { wordlist } from "@scure/bip39/wordlists/english.js";
import { useRouter } from "expo-router";
import React, { useEffect, useState } from "react";
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  View,
} from "react-native";
import { ActivityIndicator, Button, useTheme } from "react-native-paper";
import { SafeAreaView } from "react-native-safe-area-context";
import { useShallow } from "zustand/shallow";

export default function Setup() {
  const router = useRouter();
  const { extra } = useAppConfig();
  const theme = useTheme() as AppTheme;

  const [isReady, status] = useConnector(
    useShallow((state) => [state.isReady, state.status])
  );

  const setup = useConnector((state) => state.setup);

  const [mnemonic, setMnemonic] = useState<string | null>(null);
  const [wasReadyOnMount] = useState(isReady);
  const [words, setWords] = useState<string[]>(
    Array(extra.MNEMONIC_LENGTH).fill("")
  );

  useEffect(() => {
    // Auto-navigate to connection ONLY if we finished setup while being here
    if (isReady && !wasReadyOnMount) router.replace("/connection");
  }, [isReady, wasReadyOnMount, router]);

  useEffect(() => {
    if (isReady) setMnemonic(ConnectorModule.getMnemonic());
  }, [isReady]);

  const handleSave = async () => {
    try {
      const phrase = words.join("-");
      await setup(phrase, extra.ENCRYPTION_SALT);
      await ConnectorModule.start();
      // Only navigate here, after manual setup completion
      router.replace("/connection");
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
            setMnemonic(null);
            setWords(Array(extra.MNEMONIC_LENGTH).fill(""));
            // After reset, brokerStatus becomes 'idle', trigger UI update
          },
        },
      ]
    );
  };

  const isComplete = words.every(
    (word) => word.length > 0 && wordlist.includes(word)
  );

  const isLoading =
    status === STATUS.ENCRYPTING ||
    status === STATUS.KEYS_READY ||
    status === STATUS.TRANSPORT_INITIALIZING ||
    status === STATUS.DISCOVERING ||
    status === STATUS.CONNECTING;

  return (
    <View
      style={[styles.container, { backgroundColor: theme.colors.background }]}
    >
      {isLoading ? (
        <View style={styles.loaderContainer}>
          <ActivityIndicator size="large" color={theme.colors.primary} />
        </View>
      ) : (
        <>
          <KeyboardAvoidingView
            behavior={Platform.OS === "ios" ? "padding" : "height"}
            style={styles.container}
          >
            <ScrollView contentContainerStyle={styles.scrollContent}>
              <View style={styles.mainContent}>
                {isReady ? (
                  <MnemonicDisplay mnemonic={mnemonic} onReset={handleReset} />
                ) : (
                  <MnemonicForm
                    words={words}
                    onWordsChange={setWords}
                    length={extra.MNEMONIC_LENGTH}
                  />
                )}
              </View>
            </ScrollView>
          </KeyboardAvoidingView>
          <SafeAreaView edges={["bottom"]} style={styles.bottomActions}>
            {isReady ? (
              <Button
                accessibilityLabel="Reset Setup"
                mode="contained"
                style={styles.btnAction}
                contentStyle={styles.btnContent}
                buttonColor={theme.colors.errorMuted}
                textColor={theme.colors.error}
                onPress={handleReset}
                labelStyle={styles.btnText}
              >
                Reset Security
              </Button>
            ) : (
              <Button
                accessibilityLabel="Start Magic Sync"
                mode="contained"
                style={[styles.btnAction, !isComplete && { opacity: 0.5 }]}
                contentStyle={styles.btnContent}
                buttonColor={theme.colors.primary}
                textColor={theme.colors.onPrimary}
                onPress={handleSave}
                disabled={!isComplete}
                labelStyle={styles.btnText}
              >
                Start Magic Sync
              </Button>
            )}
          </SafeAreaView>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  loaderContainer: {
    flex: 0.9,
    justifyContent: "center",
    alignItems: "center",
  },
  scrollContent: {
    flexGrow: 1,
    padding: 24,
  },
  mainContent: {
    alignItems: "center",
    width: "100%",
  },
  bottomActions: {
    paddingHorizontal: 24,
    paddingBottom: 24,
  },
  btnAction: {
    width: "100%",
    borderRadius: 22,
    marginBottom: 10,
  },
  btnContent: {
    paddingVertical: 10,
  },
  btnText: {
    fontSize: 16,
    fontWeight: "bold",
  },
});
