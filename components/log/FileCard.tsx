import { Blob } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import React from "react";
import { StyleSheet, View } from "react-native";
import { Avatar, Card, Text } from "react-native-paper";
import { MessageHeader } from "./MessageHeader";

const formatBytes = (bytes: number, decimals = 2) => {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + " " + sizes[i];
};

export const FileCard = ({ item, theme }: { item: Blob; theme: AppTheme }) => {
  return (
    <Card
      style={[styles.logCard, { borderColor: theme.colors.cardBorder }]}
      mode="elevated"
      elevation={1}
    >
      <Card.Content style={styles.cardContent}>
        <View style={styles.iconContainer}>
          <Avatar.Icon
            size={40}
            icon={
              item.blobType === "IMAGE"
                ? "image-outline"
                : "file-download-outline"
            }
            style={{ backgroundColor: theme.colors.surfaceVariant }}
            color={theme.colors.primary}
          />
        </View>
        <View style={styles.logInfo}>
          <MessageHeader item={item} theme={theme} />
          <Text
            variant="titleMedium"
            numberOfLines={1}
            style={{ color: theme.colors.onSurface }}
          >
            {item.name || "Large text blob"}
          </Text>
          <Text
            variant="bodySmall"
            style={{ color: theme.colors.onSurfaceVariant }}
          >
            {formatBytes(item.size)} • Saved to Downloads
          </Text>
        </View>
      </Card.Content>
    </Card>
  );
};

const styles = StyleSheet.create({
  logCard: {
    borderRadius: 20,
    marginBottom: 12,
    borderWidth: 1,
  },
  cardContent: {
    paddingHorizontal: 16,
    paddingVertical: 14,
    flexDirection: "row",
    alignItems: "center",
  },
  iconContainer: {
    marginRight: 16,
  },
  logInfo: {
    flex: 1,
  },
});
