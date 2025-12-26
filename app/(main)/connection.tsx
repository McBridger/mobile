import { Item, useConnector } from "@/modules/connector";
import React, { useMemo } from "react";
import { FlatList, StyleSheet, View } from "react-native";
import { useShallow } from "zustand/react/shallow";
import { useTheme, Button, Card, Text } from "react-native-paper";

export default function Connection() {
  const brokerStatus = useConnector((state) => state.brokerStatus);
  const isConnected = brokerStatus === "connected";
  const theme = useTheme();

  const _items = useConnector((state) => state.items);
  const items = useMemo(
    () => Array.from(_items.values()).sort((a, b) => b.time - a.time) as Item[],
    [_items]
  );

  const [disconnect] = useConnector(useShallow((state) => [state.disconnect]));

  const renderItem = ({ item }: { item: Item }) => (
    <View style={styles.clipboardItem}>
      <Card style={{ backgroundColor: theme.colors.surfaceContainerHigh }}>
        <Card.Content>
          <Text variant="titleMedium" style={{ color: theme.colors.primary }}>
            {item.type === "sent" ? "Sent:" : "Received:"}
          </Text>
          <Text style={styles.itemContent}>{item.content}</Text>
          <Text variant="bodySmall" style={styles.itemTimestamp}>
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

      {isConnected && (
        <View style={styles.footer}>
          <Button
            textColor={theme.colors.onSurface}
            buttonColor={theme.colors.primaryContainer}
            mode="outlined"
            labelStyle={{ fontSize: 16, fontWeight: 'bold' }} 
            onPress={disconnect}
          >
            Disconnect
          </Button>
        </View>
      )}
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
  itemContent: {
    marginBottom: 8,
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
  footer: {
    position: "absolute",
    bottom: 30,
    left: 0,
    right: 0,
    alignItems: "center",
  },
});
