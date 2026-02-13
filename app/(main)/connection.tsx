import { ClipboardCard } from "@/components/log/ClipboardCard";
import { FileCard } from "@/components/log/FileCard";
import { BleState, Message, useConnector } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import { Redirect } from "expo-router";
import React, { useMemo } from "react";
import { FlatList, StyleSheet, View } from "react-native";
import { Text, useTheme } from "react-native-paper";

export default function Connection() {
  const isReady = useConnector((v) => v.isReady);
  const isConnected = useConnector(
    (v) => v.state.ble.current === BleState.CONNECTED,
  );
  const theme = useTheme() as AppTheme;

  const _items = useConnector((state) => state.items);
  const items = useMemo(
    () =>
      Array.from(_items.values()).sort(
        (a, b) => b.timestamp - a.timestamp,
      ) as Message[],
    [_items],
  );

  if (!isReady) return <Redirect href="/setup" />;

  const renderItem = ({ item }: { item: Message }) => {
    switch (item.type) {
      case "TINY":
        return <ClipboardCard item={item} theme={theme} />;
      case "BLOB":
        return <FileCard item={item} theme={theme} />;
      default:
        return null;
    }
  };

  return (
    <View
      style={[styles.container, { backgroundColor: theme.colors.background }]}
    >
      <FlatList
        data={items}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text
              variant="titleMedium"
              style={[
                styles.emptyText,
                { color: theme.colors.onSurfaceVariant },
              ]}
            >
              {isConnected
                ? "No data synced yet. Try copying something on your Mac"
                : "Waiting for connection to start syncing..."}
            </Text>
          </View>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  listContent: {
    padding: 20,
  },
  emptyContainer: {
    padding: 40,
    alignItems: "center",
  },
  emptyText: {
    textAlign: "center",
  },
});
