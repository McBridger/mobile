import React, { useMemo } from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { Card, Text, useTheme, type MD3Theme } from "react-native-paper";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { BleDevice } from "@/modules/scanner";

const getStyles = (theme: MD3Theme) =>
  StyleSheet.create({
    card: {
      marginBottom: 10,
      backgroundColor: theme.colors.surfaceContainerHigh,
    },
    cardPressed: {
      opacity: 0.8,
    },
    cardContent: {
      marginBottom: 4,
      alignItems: "center",
      flexDirection: "row",
    },
    deviceInfoLabel: {
      marginRight: 6,
      fontWeight: "bold",
    },
    statusText: {
      marginTop: 4,
    },
    icon: {
      marginRight: 10,
    },
  });

const deviceInfoRowStyles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 4,
  },
  label: {
    marginRight: 6,
    fontWeight: "bold",
  },
});

type DeviceCardProps = {
  device: BleDevice;
  onPress: () => void;
};

type DeviceInfoRowProps = {
  label: string;
  value: string | number;
  valueColor: string;
};

const DeviceInfoRow: React.FC<DeviceInfoRowProps> = ({
  label,
  value,
  valueColor,
}) => (
  <View style={deviceInfoRowStyles.container}>
    <Text variant="bodyMedium" style={deviceInfoRowStyles.label}>
      {label}:
    </Text>
    <Text variant="bodyMedium" style={{ color: valueColor }}>
      {value}
    </Text>
  </View>
);

const DeviceCard: React.FC<DeviceCardProps> = ({ device, onPress }) => {
  const theme = useTheme();

  const styles = useMemo(() => getStyles(theme), [theme]);

  const infoColor = theme.colors.onSurfaceVariant;

  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [{ opacity: pressed ? 0.8 : 1.0 }]}
    >
      <Card style={styles.card}>
        <Card.Content>
          <View style={styles.cardContent}>
            <Icon
              name="laptop"
              size={16}
              color={theme.colors.primary}
              style={styles.icon}
            />
            <Text variant="titleSmall" style={{ color: infoColor }}>
              {device.name || "N/A"}
            </Text>
          </View>

          <DeviceInfoRow
            label="Address"
            value={device.address}
            valueColor={infoColor}
          />
          <DeviceInfoRow
            label="RSSI"
            value={device.rssi}
            valueColor={infoColor}
          />

          {device.isBridger && (
            <Text
              variant="labelSmall"
              style={[styles.statusText, { color: theme.colors.tertiary }]}
            >
              [Bridger Service Found]
            </Text>
          )}
        </Card.Content>
      </Card>
    </Pressable>
  );
};

export default DeviceCard;
