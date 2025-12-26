import React, { useState, useEffect } from "react";
import {
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Alert,
} from "react-native";
import { useRouter } from "expo-router";
import ConnectorModule from "@/modules/connector";
import { useAppConfig } from "@/hooks/useConfig";
import { MnemonicDisplay } from "@/components/MnemonicDisplay";
import { MnemonicForm } from "@/components/MnemonicForm";

export default function Setup() {
  const router = useRouter();
  const { extra } = useAppConfig();
  
  const [isReady, setIsReady] = useState(false);
  const [mnemonic, setMnemonic] = useState<string | null>(null);

  useEffect(() => {
    const ready = ConnectorModule.isReady();
    setIsReady(ready);
    if (ready) setMnemonic(ConnectorModule.getMnemonic());
  }, []);

  const handleSave = async (phrase: string) => {
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
          } 
        }
      ]
    );
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === "ios" ? "padding" : "height"}
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {isReady ? (
          <MnemonicDisplay mnemonic={mnemonic} onReset={handleReset} />
        ) : (
          <MnemonicForm 
            length={extra.MNEMONIC_LENGTH} 
            onSave={handleSave} 
          />
        )}
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
});
