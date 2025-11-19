import * as Clipboard from "expo-clipboard";
import React, { useCallback, useMemo, useState } from "react";
import { FlatList, StyleSheet, View } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { Snackbar, useTheme } from "react-native-paper";

import { ConnectionBottomBar } from "@/components/ConnectionBottomBar";
import { MessageItem } from "@/components/MessageItem";
import { useConnection } from "@/hooks/useConnection";
import { Item } from "@/modules/connector";

export default function Connection() {
  const theme = useTheme();
  const styles = useMemo(() => getStyles(theme), [theme]);

  const { items, send, deleteItem, addItem, clearItems, disconnect } =
    useConnection();

  const [lastDeletedItem, setLastDeletedItem] = useState<Item | null>(null);
  const [showUndo, setShowUndo] = useState(false);

  const handleRemoveItem = useCallback(
    (item: Item) => {
      setLastDeletedItem(item);
      setShowUndo(true);
      deleteItem(item.id);
    },
    [deleteItem]
  );

  const handleUndo = useCallback(() => {
    if (lastDeletedItem) {
      addItem(lastDeletedItem);
    }
    setShowUndo(false);
    setLastDeletedItem(null);
  }, [lastDeletedItem, addItem]);

  const handleSendText = useCallback(
    async (text: string) => {
      await Clipboard.setStringAsync(text);
      send(text);
    },
    [send]
  );

  const renderItem = ({ item }: { item: Item }) => (
    <MessageItem
      item={item}
      onSend={handleSendText}
      onSwipeToDelete={handleRemoveItem}
    />
  );

  return (
    <View style={styles.container}>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <FlatList
          data={items}
          renderItem={renderItem}
          keyExtractor={(item) => item.id}
          style={styles.list}
          inverted
          showsVerticalScrollIndicator={false}
        />
      </GestureHandlerRootView>

      <ConnectionBottomBar onClear={clearItems} onDisconnect={disconnect} />

      <Snackbar
        visible={showUndo}
        onDismiss={() => setShowUndo(false)}
        action={{ label: "Undo", onPress: handleUndo }}
        duration={5000}
      >
        Item has been deleted.
      </Snackbar>
    </View>
  );
}

const getStyles = (theme: ReturnType<typeof useTheme>) =>
  StyleSheet.create({
    container: {
      flex: 1,
      paddingHorizontal: 10,
      paddingTop: 10,
      backgroundColor: theme.colors.background,
      paddingBottom: 60,
    },
    list: {
      flex: 1,
    },
  });
