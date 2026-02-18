import { Porter, BridgerType, PorterStatus } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import React from "react";
import { StyleSheet, View } from "react-native";
import { Avatar, Card, Text, ProgressBar } from "react-native-paper";
import { MessageHeader } from "./MessageHeader";

const formatBytes = (bytes: number, decimals = 1) => {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(decimals)) + " " + sizes[i];
};

export const PorterCard = ({ item, theme }: { item: Porter; theme: AppTheme }) => {
  const isCompleted = item.status === PorterStatus.COMPLETED;
  const isActive = item.status === PorterStatus.ACTIVE;
  const isError = item.status === PorterStatus.ERROR;
  
  // Logic for what to show as the main "body":
  // Completed, non-truncated text goes directly into view.
  // Everything else (files, images, large/active text) shows as a task with meta/progress.
  const showContentDirectly = item.type === BridgerType.TEXT && isCompleted && !item.isTruncated;

  const getIcon = () => {
    if (item.type === BridgerType.IMAGE) return "image-outline";
    if (item.type === BridgerType.TEXT) return "clipboard-text-outline";
    return "file-download-outline";
  };

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
            icon={getIcon()} 
            style={{ backgroundColor: theme.colors.surfaceVariant }} 
            color={isError ? theme.colors.error : theme.colors.primary} 
          />
        </View>
        
        <View style={styles.logInfo}>
          <MessageHeader item={item} theme={theme} />

          {showContentDirectly ? (
            <Text variant="bodyLarge" style={{ color: theme.colors.onSurface }} numberOfLines={5}>
              {item.data || "Empty content"}
            </Text>
          ) : (
            <View>
              <Text variant="titleMedium" numberOfLines={1} style={{ color: theme.colors.onSurface }}>
                {item.name || (item.type === BridgerType.TEXT ? "Large Text Stream" : "Unknown data")}
              </Text>
              <Text variant="bodySmall" style={{ color: theme.colors.onSurfaceVariant, marginTop: 2 }}>
                {formatBytes(item.currentSize)} / {formatBytes(item.totalSize)}
                {isCompleted && item.type !== BridgerType.TEXT && " • Saved"}
              </Text>
            </View>
          )}

          {isActive && (
            <View style={styles.progressContainer}>
              <ProgressBar 
                progress={item.progress / 100} 
                color={theme.colors.primary} 
                style={styles.progressBar} 
              />
              <Text variant="labelSmall" style={styles.progressText}>{item.progress}%</Text>
            </View>
          )}

          {item.isTruncated && showContentDirectly && (
            <Text variant="labelSmall" style={{ color: theme.colors.outline, marginTop: 4 }}>
              Preview truncated
            </Text>
          )}

          {item.error && (
            <Text variant="labelSmall" style={{ color: theme.colors.error, marginTop: 4 }}>
              Error: {item.error}
            </Text>
          )}
        </View>
      </Card.Content>
    </Card>
  );
};

const styles = StyleSheet.create({
    logCard: { 
        borderRadius: 20, 
        marginBottom: 12, 
        borderWidth: 1 
    },
    cardContent: { 
        paddingHorizontal: 16, 
        paddingVertical: 14, 
        flexDirection: "row", 
        alignItems: "flex-start" 
    },
    iconContainer: { 
        marginRight: 16, 
        marginTop: 4 
    },
    logInfo: { 
        flex: 1 
    },
    progressContainer: { 
        marginTop: 8, 
        flexDirection: "row", 
        alignItems: "center", 
        gap: 8 
    },
    progressBar: { 
        flex: 1, 
        height: 6, 
        borderRadius: 3 
    },
    progressText: { 
        width: 35, 
        textAlign: "right" 
    }
});
