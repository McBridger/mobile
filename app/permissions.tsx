import Header from "@/components/Header";
import { useBluetoothPermissions } from "@/hooks/useBluetoothPermissions";
import { AppTheme } from "@/theme/CustomTheme";
import { Ionicons } from "@expo/vector-icons";
import { Stack, useRouter } from "expo-router";
import React, { useEffect, useMemo } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import { Avatar, Button, Card, List, Text, useTheme } from "react-native-paper";
import { PERMISSIONS, RESULTS } from "react-native-permissions";

export default function PermissionsScreen() {
  const theme = useTheme() as AppTheme;
  const router = useRouter();
  const { status, isLoading, request, showPermissionRationale } =
    useBluetoothPermissions();

  const permissionsState = useMemo(() => {
    const android = status.android || {};
    const btScan = android[PERMISSIONS.ANDROID.BLUETOOTH_SCAN];
    const btConnect = android[PERMISSIONS.ANDROID.BLUETOOTH_CONNECT];
    const location = android[PERMISSIONS.ANDROID.ACCESS_FINE_LOCATION];
    const notifications = android["NOTIFICATIONS"];

    const isBtGranted =
      btScan === RESULTS.GRANTED && btConnect === RESULTS.GRANTED;
    const isLocationGranted = location === RESULTS.GRANTED;
    const isNotificationsGranted = notifications === RESULTS.GRANTED;

    return {
      isBtGranted,
      isLocationGranted,
      isNotificationsGranted,
      isMandatoryGranted: isBtGranted && isLocationGranted,
    };
  }, [status]);

  useEffect(() => {
    if (!isLoading && permissionsState.isMandatoryGranted) {
      // Small delay for smooth transition so user sees the last checkbox checked
      const timer = setTimeout(() => router.replace("/"), 500);
      return () => clearTimeout(timer);
    }
  }, [isLoading, permissionsState.isMandatoryGranted, router]);

  if (isLoading) {
    return (
      <View
        style={[styles.container, { backgroundColor: theme.colors.background }]}
      >
        <Text>Checking permissions...</Text>
      </View>
    );
  }

  return (
    <View
      style={[styles.container, { backgroundColor: theme.colors.background }]}
    >
      <Stack.Screen options={{ headerShown: false }} />
      <Header />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.hero}>
          <Avatar.Icon
            size={56}
            icon="shield-check-outline"
            style={{
              backgroundColor: theme.colors.primaryMuted,
              marginBottom: 12,
            }}
            color={theme.colors.primary}
          />
          <Text variant="headlineSmall" style={styles.heroTitle}>
            Safety First
          </Text>
          <Text
            variant="bodyMedium"
            style={[
              styles.heroSubtitle,
              { color: theme.colors.onSurfaceVariant },
            ]}
          >
            McBridge requires access to some features to ensure a seamless sync
            between your devices.
          </Text>
        </View>

        <Card
          style={[styles.card, { borderColor: theme.colors.cardBorder }]}
          mode="outlined"
        >
          <Card.Content>
            <List.Section style={styles.listSection}>
              <List.Subheader style={styles.listSubheader}>
                Core Connectivity (Mandatory)
              </List.Subheader>

              <List.Item
                title="Bluetooth & Discovery"
                description="Required to find and talk to your Mac."
                left={(props) => (
                  <Avatar.Icon
                    {...props}
                    size={40}
                    icon="bluetooth"
                    style={{
                      backgroundColor: permissionsState.isBtGranted
                        ? theme.colors.connected + "20"
                        : theme.colors.surfaceVariant,
                    }}
                    color={
                      permissionsState.isBtGranted
                        ? theme.colors.connected
                        : theme.colors.onSurfaceVariant
                    }
                  />
                )}
                right={() =>
                  permissionsState.isBtGranted ? (
                    <View style={styles.statusIcon}>
                      <Ionicons
                        name="checkmark-circle"
                        size={24}
                        color={theme.colors.connected}
                      />
                    </View>
                  ) : null
                }
              />

              <List.Item
                title="Location Services"
                description="Android requirement for Bluetooth scanning."
                left={(props) => (
                  <Avatar.Icon
                    {...props}
                    size={40}
                    icon="map-marker-radius"
                    style={{
                      backgroundColor: permissionsState.isLocationGranted
                        ? theme.colors.connected + "20"
                        : theme.colors.surfaceVariant,
                    }}
                    color={
                      permissionsState.isLocationGranted
                        ? theme.colors.connected
                        : theme.colors.onSurfaceVariant
                    }
                  />
                )}
                right={() =>
                  permissionsState.isLocationGranted ? (
                    <View style={styles.statusIcon}>
                      <Ionicons
                        name="checkmark-circle"
                        size={24}
                        color={theme.colors.connected}
                      />
                    </View>
                  ) : null
                }
              />
            </List.Section>
          </Card.Content>
        </Card>

        <Card
          style={[styles.card, { borderColor: theme.colors.cardBorder }]}
          mode="outlined"
        >
          <Card.Content>
            <List.Section style={styles.listSection}>
              <List.Subheader style={styles.listSubheader}>
                Experience (Recommended)
              </List.Subheader>
              <List.Item
                title="Instant Notifications"
                description="Stay updated on sync status in real-time."
                left={(props) => (
                  <Avatar.Icon
                    {...props}
                    size={40}
                    icon="bell-ring"
                    style={{
                      backgroundColor: permissionsState.isNotificationsGranted
                        ? theme.colors.connected + "20"
                        : theme.colors.surfaceVariant,
                    }}
                    color={
                      permissionsState.isNotificationsGranted
                        ? theme.colors.connected
                        : theme.colors.onSurfaceVariant
                    }
                  />
                )}
                right={() =>
                  permissionsState.isNotificationsGranted ? (
                    <View style={styles.statusIcon}>
                      <Ionicons
                        name="checkmark-circle"
                        size={24}
                        color={theme.colors.connected}
                      />
                    </View>
                  ) : null
                }
              />
            </List.Section>
          </Card.Content>
        </Card>

        <View style={styles.footer}>
          <Button
            mode="contained"
            onPress={request}
            disabled={permissionsState.isMandatoryGranted}
            style={[
              styles.mainButton,
              permissionsState.isMandatoryGranted && { opacity: 0.5 },
            ]}
            contentStyle={styles.buttonContent}
            labelStyle={styles.buttonLabel}
          >
            {permissionsState.isMandatoryGranted
              ? "Mandatory Granted"
              : "Grant Access"}
          </Button>

          <Button
            mode="text"
            onPress={showPermissionRationale}
            textColor={theme.colors.onSurfaceVariant}
          >
            Why do I need this?
          </Button>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    padding: 20,
    paddingTop: 10,
    paddingBottom: 20,
  },
  hero: {
    alignItems: "center",
    marginVertical: 20,
  },
  heroTitle: {
    fontWeight: "bold",
    marginBottom: 8,
  },
  heroSubtitle: {
    textAlign: "center",
    marginTop: 8,
    paddingHorizontal: 20,
  },
  listSection: {
    marginVertical: 0,
  },
  listSubheader: {
    fontSize: 16,
    fontWeight: "bold",
  },
  card: {
    marginBottom: 16,
    borderRadius: 16,
    borderWidth: 1,
  },
  statusIcon: {
    justifyContent: "center",
    paddingLeft: 8,
  },
  footer: {
    marginTop: 20,
    alignItems: "center",
  },
  mainButton: {
    width: "100%",
    borderRadius: 28,
    marginBottom: 12,
  },
  buttonContent: {
    paddingVertical: 8,
  },
  buttonLabel: {
    fontSize: 16,
    fontWeight: "bold",
  },
});
