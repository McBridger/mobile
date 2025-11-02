import { useAppConfig } from "@/hooks/useConfig";
import { Item, useConnector } from "@/modules/connector";
import { Ionicons } from "@expo/vector-icons";
import * as Clipboard from "expo-clipboard";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import React, { useCallback, useEffect, useMemo } from "react";


import {
  FlatList,
  StyleSheet,
  Text,
  TouchableHighlight,
  TouchableOpacity,
  View,
} from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import Reanimated, {
  Extrapolation,
  SharedValue,
  interpolate,
  useAnimatedStyle,
} from "react-native-reanimated";

import Swipeable from "react-native-gesture-handler/ReanimatedSwipeable";

import { useShallow } from "zustand/react/shallow";

const COLORS = {
  // BACKGROUND: "linear-gradient(90deg,rgba(56, 54, 57, 1) 0%, rgba(88, 84, 84, 1) 31%, rgba(120, 114, 111, 1) 100%)",
  BACKGROUND: "#8c8582",
  // HEADER_BG: "#080707ff",
  TEXT: "#ffffff",
  SENT_BG: "#413e3f",
  RECEIVED_BG: "#404347",
  DELETE_BG: "#b37170",
  DISCONNECT: "#805150",
  BTN_TEXT: "#000000",
};

const SPACINGS = {
  BORDER_RADIUS: 20,
};

export default function Connection() {
  const router = useRouter();
  const { extra } = useAppConfig();

  const params = useLocalSearchParams<{ address: string; name: string }>();
  const address = useMemo(() => params.address, [params.address]);
  const name = useMemo(() => params.name, [params.name]);

  const status = useConnector((state) => state.status);
  const isConnected = useMemo(() => status === "connected", [status]);

  const _items = useConnector((state) => state.items);
  const items = useMemo(
    () => Array.from(_items.values()).sort((a, b) => b.time - a.time) as Item[],
    [_items]
  );

  const [
    connect,
    disconnect,
    send,
    deleteItem,
    addItem,
    clearItems,
    // addRecorded
  ] = useConnector(
    useShallow((state) => [
      state.connect,
      state.disconnect,
      state.send,
      state.deleteItem,
      state.addItem,
      state.clearItems,
      // state.addRecorded
    ])
  );

  useFocusEffect(
    useCallback(() => {
      if (isConnected) return;
      if (!address) return;

      connect(address, name, extra);
      // bleRecorder.processEntries().then((entries) => addRecorded(entries));
    }, [address, connect, extra, isConnected, name])
  );

  useEffect(() => {
    const unsub = useConnector.subscribe(
      (state) => state.status,
      (status, prevStatus) => {
        if (prevStatus === "disconnecting" && status === "disconnected")
          router.push("/devices");
      }
    );

    return () => {
      unsub();
    };
  }, [router]);

  const [lastDeletedItem, setLastDeletedItem] = React.useState(null);
  const [showUndo, setShowUndo] = React.useState(false);

  const UndoSnackbar = ({ onUndo }) => (
    <View style={styles.undoSnackbar}>
      <Text style={styles.undoText}>Запись удалена. Отменить?</Text>
      <TouchableOpacity onPress={onUndo} style={styles.undoButton}>
        <Text style={styles.undoButtonText}>ОТМЕНИТЬ</Text>
      </TouchableOpacity>
    </View>
  );

  const handleSendText = async (text: string) => {
    console.log("You long-pressed the button!", text);
    try {
      await Clipboard.setStringAsync(text);
      send(text);
    } catch (error) {
      console.error("Failed to copy to clipboard", error);
    }
  };

  const handleRemoveItem = useCallback(
    (item: Item) => {
      setLastDeletedItem(item);
      setShowUndo(true);
      deleteItem(item.id);

      const timer = setTimeout(() => {
        setShowUndo(false);
        setLastDeletedItem(null);
      }, 5000);

      return () => clearTimeout(timer);
    },
    [deleteItem]
  );

  const handleUndo = useCallback(() => {
    if (lastDeletedItem) {
      addItem(lastDeletedItem);
    }
    setShowUndo(false);
    setLastDeletedItem(null);
  }, [lastDeletedItem]);

  const handleSwipeOpen = useCallback(
    (direction: "left" | "right", item: Item) => {
      if (direction === "left") {
        handleRemoveItem(item);
      }
    },
    [handleRemoveItem]
  );

  const getItemMarginStyles = (isSent: boolean) => {
    return isSent
      ? { marginLeft: "10%", marginRight: 0 }
      : { marginRight: "10%", marginLeft: 0 };
  };

  const createRightAction =
    (isSent: boolean) =>
    (progress: SharedValue<number>, dragX: SharedValue<number>) => {
      const scale = useAnimatedStyle(() => {
        const currentOpacity = progress.value > 0.01 ? 1 : 0;

        // Анимация масштаба иконки
        const finalScale = interpolate(
          progress.value,
          [0, 1],
          [0.6, 1],
          Extrapolation.CLAMP
        );
        return {
          opacity: currentOpacity,
          transform: [{ scale: finalScale }],
        };
      });

      const actionContainerStyle = [
        styles.rightActionContainer,
        getItemMarginStyles(isSent),
      ];

      return (
        <View style={actionContainerStyle}>
          <Reanimated.View style={[styles.actionIconWrapper, scale]}>
            {/* <Text style={styles.actionIcon}>🗑</Text> */}
                    <Ionicons name="trash" size={32} color="white" />

          </Reanimated.View>
        </View>
      );
    };

  const renderItem = ({ item }: { item: Item; onRemove: void }) => {
    const isSent = item.type === "sent";

    const wrapperStyle = [
      styles.cllipboardWrapper,
      getItemMarginStyles(isSent),
      { backgroundColor: isSent ? COLORS.SENT_BG : COLORS.RECEIVED_BG },
    ];

    return (
      <Swipeable
        renderRightActions={createRightAction(isSent)}
        friction={1.2}
        overshootRight={true}
        overshootFriction={0.5}
        overshootLeft={false}
        rightThreshold={50}
        enableTrackpadTwoFingerGesture
        onSwipeableOpen={(direction) => handleSwipeOpen(direction, item)}
      >
        <TouchableHighlight
          style={wrapperStyle}
          onPress={() => handleSendText(item.content)}
          onLongPress={() => handleSendText(item.content)}
          underlayColor={isSent ? COLORS.SENT_BG : COLORS.RECEIVED_BG}
        >
          <View style={styles.clipboardItem}>
            <Text style={styles.itemType}>
              {isSent ? "Sent:" : "Received:"}
            </Text>
            <Text style={styles.itemContent}>{item.content}</Text>
            <Text style={styles.itemTimestamp}>
              {new Date(item.time).toLocaleTimeString()}
            </Text>
          </View>
        </TouchableHighlight>
      </Swipeable>
    );
  };
  return (
    <View style={styles.container}>
      {/* <Text style={styles.title}>Connection Details</Text> */}
      <GestureHandlerRootView>
        <FlatList
          data={items}
          renderItem={renderItem}
          keyExtractor={(item) => item.id}
          style={styles.list}
        />
      </GestureHandlerRootView>
      <View style={styles.footer}>
        <TouchableOpacity style={styles.button} onPress={clearItems}>
          <Text style={styles.buttonText}>Remove all</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.button} onPress={disconnect}>
          <Text style={styles.buttonText}>Disconnect</Text>
        </TouchableOpacity>
      </View>
      {showUndo && <UndoSnackbar onUndo={handleUndo} />}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: COLORS.BACKGROUND,
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 20,
    textAlign: "center",
  },
  cllipboardWrapper: {
    borderRadius: SPACINGS.BORDER_RADIUS,
    marginBottom: 10,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
    elevation: 2,
    padding: 15,
    overflow: "hidden",
  },
  clipboardItem: {
    // color: COLORS.TEXT,
    // backgroundColor: "#fff",
    // padding: 15,
  },
  itemType: {
    fontSize: 14,
    fontWeight: "bold",
    color:  COLORS.TEXT,
    marginBottom: 5,
  },
  itemContent: {
    fontSize: 16,
    color: COLORS.TEXT,
    marginBottom: 5,
  },
  itemTimestamp: {
    fontSize: 12,
    color: COLORS.TEXT,
    textAlign: "right",
  },
  list: {
    flex: 1,
  },
  rightAction: {
    width: 50,
    height: "100%",
    verticalAlign: "middle",
    textAlign: "center",
    backgroundColor: "#f25d03",
  },
  disconnectButton: {
    position: "absolute",
    width: 120,
    bottom: 30,
    right: 30,
    backgroundColor: COLORS.DISCONNECT,
    padding: 15,
    borderRadius: 30,
    elevation: 5,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  buttonText: {
    color: COLORS.TEXT,
    fontWeight: "bold",
    fontSize: 16,
    textAlign: "center",
  },

  footer: {
    backgroundColor: "#2d2d2d",
    height: 70,
    width: "100%",
    borderRadius: 30,
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
  },

  button: {
    width: "30%",
    backgroundColor: "#f25d03",
    padding: 15,
    borderRadius: 30,
    elevation: 5,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    margin: 8
  },

  rightActionContainer: {
   backgroundColor: "#f25d03",
    flex: 1,
    justifyContent: "center",
    alignItems: "flex-end", // Aligns the icon to the right edge
    paddingRight: 20, // Space from the edge for the icon
    borderRadius: SPACINGS.BORDER_RADIUS, // Match the item border radius
    marginBottom: 10, // Match the item margin for consistent spacing
  },
  actionIconWrapper: {
    width: 30,
    height: "100%",
    justifyContent: "center",
    alignItems: "center",
  },
  actionIcon: {
    fontSize: 24,
    color: "white",
    lineHeight: 30,
  },

  // UNDO Snackbar
  undoSnackbar: {
    position: "absolute",
    bottom: 80,
    left: 20,
    right: 20,
    backgroundColor: COLORS.DELETE_BG,
    borderRadius: 8,
    padding: 15,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    zIndex: 10,
  },
  undoText: {
    color: COLORS.TEXT,
    fontSize: 16,
  },
  undoButton: {
    padding: 5,
    paddingHorizontal: 10,
    backgroundColor: COLORS.TEXT,
    borderRadius: 4,
  },
  undoButtonText: {
    color: COLORS.DELETE_BG,
    fontWeight: "bold",
  },
});
