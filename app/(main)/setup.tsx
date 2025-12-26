import { MnemonicDisplay } from "@/components/MnemonicDisplay";
import { MnemonicForm } from "@/components/MnemonicForm";
import { useAppConfig } from "@/hooks/useConfig";
import ConnectorModule, { useConnector } from "@/modules/connector";
import { useRouter } from "expo-router";
import React, { useEffect, useState } from "react";
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
} from "react-native";

export default function Setup() {
  const router = useRouter();
  const { extra } = useAppConfig();
  
  const isReady = useConnector((state) => state.isReady);
  const setup = useConnector((state) => state.setup);
  
  const [mnemonic, setMnemonic] = useState<string | null>(null);
  const [wasReadyOnMount] = useState(isReady);

  useEffect(() => {
    // Auto-navigate to connection ONLY if we finished setup while being here
    if (isReady && !wasReadyOnMount) router.replace("/connection");
  }, [isReady, wasReadyOnMount, router]);

  useEffect(() => {
    if (isReady) setMnemonic(ConnectorModule.getMnemonic());
  }, [isReady]);

  const handleSave = async (phrase: string) => {
    try {
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
            // After reset, brokerStatus becomes 'idle', trigger UI update
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
