import { MnemonicDisplay } from "@/components/MnemonicDisplay";
import { MnemonicForm } from "@/components/MnemonicForm";
import { useAppConfig } from "@/hooks/useConfig";
import ConnectorModule, { useConnector } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import { useRouter } from "expo-router";
import React, { useEffect, useState } from "react";
import {
  Alert,
  KeyboardAvoidingView,
  ScrollView,
  StyleSheet,
  View
} from "react-native";
import { Button, useTheme } from "react-native-paper";
import { SafeAreaView } from "react-native-safe-area-context";

export default function Setup() {
  const router = useRouter();
  const { extra } = useAppConfig();
  const theme = useTheme() as AppTheme;
  
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
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <KeyboardAvoidingView
        behavior="height"
        style={styles.container}
      >
        <ScrollView contentContainerStyle={styles.scrollContent}>
          <View style={styles.mainContent}>
            {isReady ? (
              <MnemonicDisplay mnemonic={mnemonic} onReset={handleReset} />
            ) : (
              <MnemonicForm 
                length={extra.MNEMONIC_LENGTH} 
                onSave={handleSave} 
              />
            )}
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
      {isReady && (
        <SafeAreaView edges={['bottom']} style={styles.bottomActions}>
          <Button 
            mode="contained"
            style={styles.btnDanger}
            contentStyle={styles.btnDangerContent}
            buttonColor={theme.colors.errorMuted} 
            textColor={theme.colors.error}
            onPress={handleReset}
            labelStyle={styles.btnDangerText}
          >
            Reset phrase setup
          </Button>
        </SafeAreaView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    padding: 24,
  },
  mainContent: {
    alignItems: "center",
    width: '100%',
  },
  bottomActions: {
    paddingHorizontal: 24,
    paddingBottom: 24,
  },
  btnDanger: {
    width: "100%",
    borderRadius: 22,
    marginBottom: 10,
  },
  btnDangerContent: {
    paddingVertical: 10,
  },
  btnDangerText: {
    fontSize: 16,
    fontWeight: "800",
  },
});
