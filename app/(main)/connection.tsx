import { Item, STATUS, useConnector } from "@/modules/connector";
import { Redirect } from "expo-router";
import React, { useMemo } from "react";
import { FlatList, StyleSheet, View } from "react-native";
import { Card, Text, useTheme } from "react-native-paper";

export default function Connection() {
  const status = useConnector((state) => state.status);
  const isReady = useConnector((state) => state.isReady);
  const isConnected = status === STATUS.CONNECTED;
  const theme = useTheme();

  const _items = useConnector((state) => state.items);
  const items = useMemo(
    () => Array.from(_items.values()).sort((a, b) => b.time - a.time) as Item[],
    [_items]
  );

  // Guard: If not ready, go to setup
  if (!isReady) return <Redirect href="/setup" />;

  const renderItem = ({ item }: { item: Item }) => (
    <View style={styles.clipboardItem}>
      <Card style={{ backgroundColor: theme.colors.surfaceContainerHigh }}>
        <Card.Content>
          <Text
            variant="titleMedium"
            style={[styles.itemType, { color: theme.colors.primary }]}
          >
            {item.type === "sent" ? "Sent:" : "Received:"}
          </Text>
          <Text variant="bodyMedium" style={styles.itemContent}>
            {item.content}
          </Text>
          <Text variant="labelMedium" style={styles.itemTimestamp}>
            {new Date(item.time).toLocaleTimeString()}
          </Text>
        </Card.Content>
      </Card>
    </View>
  );

  return (
    <View style={styles.container}>
      <FlatList
        data={items}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        style={{ backgroundColor: theme.colors.background }}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>
              {isConnected
                ? "No data synced yet. Try copying something on your Mac!"
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
  clipboardItem: {
    marginBottom: 8,
  },
  itemType: {
    fontWeight: "bold",
     marginBottom: 4,
  },
  itemContent: {
    marginBottom: 8,
    fontSize: 16,
  },
  itemTimestamp: {
    textAlign: "right",
  },
  emptyContainer: {
    padding: 40,
    alignItems: "center",
  },
  emptyText: {
    textAlign: "center",
    color: "#999",
    fontSize: 14,
    lineHeight: 20,
  },
});
