import React, { useState, useEffect } from "react";
import {
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Alert,
} from "react-native";
import { useRouter } from "expo-router";
import ConnectorModule, { useConnector } from "@/modules/connector";
import { useAppConfig } from "@/hooks/useConfig";
import { MnemonicDisplay } from "@/components/MnemonicDisplay";
import { MnemonicForm } from "@/components/MnemonicForm";

export default function Setup() {
  const router = useRouter();
  const { extra } = useAppConfig();
  
  const brokerStatus = useConnector((state) => state.brokerStatus);
  const setup = useConnector((state) => state.setup);
  
  const [mnemonic, setMnemonic] = useState<string | null>(null);

  useEffect(() => {
    if (ConnectorModule.isReady()) {
      setMnemonic(ConnectorModule.getMnemonic());
    }
  }, [brokerStatus]);

  const handleSave = async (phrase: string) => {
    try {
      await setup(phrase, extra.ENCRYPTION_SALT);
      await ConnectorModule.start();
      // Only navigate here, after manual setup completion
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
            setMnemonic(null);
            // After reset, brokerStatus becomes 'idle', trigger UI update
          } 
        }
      ]
    );
  };

  // Only show MnemonicDisplay if we are actually past the setup phase
  const isSetupDone = brokerStatus !== "idle" && brokerStatus !== "encrypting" && brokerStatus !== "error";

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === "ios" ? "padding" : "height"}
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {isSetupDone ? (
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
