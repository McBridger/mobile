import { File } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import React from "react";
import { StyleSheet, View } from "react-native";
import { Avatar, Card, Text } from "react-native-paper";
import { MessageHeader } from "./MessageHeader";

export const FileCard = ({ item, theme }: { item: File; theme: AppTheme }) => (
    <Card style={[styles.logCard, { borderColor: theme.colors.cardBorder }]} mode="elevated" elevation={1}>
        <Card.Content style={styles.cardContent}>
            <View style={styles.iconContainer}>
                <Avatar.Icon size={40} icon="file-download-outline" style={{ backgroundColor: theme.colors.surfaceVariant }} color={theme.colors.primary} />
            </View>
            <View style={styles.logInfo}>
                <MessageHeader item={item} theme={theme} />
                <Text variant="titleMedium" numberOfLines={1} style={{ color: theme.colors.onSurface }}>
                    {item.name}
                </Text>
                <Text variant="bodySmall" style={{ color: theme.colors.onSurfaceVariant }}>
                    {item.size} • Cloud sync ready
                </Text>
            </View>
        </Card.Content>
    </Card>
);

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
