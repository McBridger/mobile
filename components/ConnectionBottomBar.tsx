import React from "react";
import { StyleSheet, View } from "react-native";
import { Appbar, Button, useTheme } from "react-native-paper";

type Props = {
  onClear: () => void;
  onDisconnect: () => void;
};

export const ConnectionBottomBar: React.FC<Props> = ({
  onClear,
  onDisconnect,
}) => {
  const theme = useTheme();
  const styles = getStyles(theme);

  return (
    <Appbar
      style={[styles.bottomBar, { backgroundColor: theme.colors.background }]}
    >
      <View style={styles.buttonContainer}>
        <Button style={styles.button} mode="contained-tonal" onPress={onClear}>
          Remove all
        </Button>
        <Button
          mode="contained-tonal"
          style={styles.button}
          onPress={onDisconnect}
        >
          Disconnect
        </Button>
      </View>
    </Appbar>
  );
};

const getStyles = (theme: ReturnType<typeof useTheme>) =>
  StyleSheet.create({
    bottomBar: {
      position: "absolute",
      left: 0,
      right: 0,
      bottom: 0,
      elevation: 4,
      height: 60,
      paddingHorizontal: 10,
      justifyContent: "center",
    },
    buttonContainer: {
      flexDirection: "row",
      justifyContent: "space-between",
      flex: 1,
    },
    button: {
      flex: 1,
      marginHorizontal: 5,
    },
  });
