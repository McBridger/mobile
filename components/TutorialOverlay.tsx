import { useTutorialStore } from "@/hooks/useTutorialStore";
import { AppTheme } from "@/theme/CustomTheme";
import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  BackHandler,
  Dimensions,
  Keyboard,
  LayoutChangeEvent,
  StyleSheet,
  View,
} from "react-native";
import { Button, Portal, Surface, Text, useTheme } from "react-native-paper";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from "react-native-reanimated";

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get("window");

export interface TutorialStep {
  targetKey?: string;
  title: string;
  description: string;
}

interface Props {
  visible: boolean;
  steps: TutorialStep[];
  onFinish: () => void;
}

export const TutorialOverlay = ({ visible, steps, onFinish }: Props) => {
  const theme = useTheme() as AppTheme;
  const { currentStep, nextStep, targets, isTutorialVisible, finishTutorial } =
    useTutorialStore();

  const containerRef = useRef<View>(null);
  const [tooltipHeight, setTooltipHeight] = useState(0);

  const holeX = useSharedValue(SCREEN_WIDTH / 2);
  const holeY = useSharedValue(SCREEN_HEIGHT / 2);
  const holeW = useSharedValue(0);
  const holeH = useSharedValue(0);
  const opacity = useSharedValue(0);

  const syncLayout = useCallback(() => {
    if (!visible) return;

    const step = steps[currentStep];
    const targetRef = step?.targetKey ? targets[step.targetKey] : null;

    if (targetRef?.current && containerRef.current) {
      targetRef.current.measureInWindow((tx, ty, tw, th) => {
        containerRef.current?.measureInWindow((cx, cy) => {
          const relativeX = tx - cx;
          const relativeY = ty - cy;

          if (tw > 0 && th > 0) {
            holeX.value = withTiming(relativeX - 4, { duration: 300 });
            holeY.value = withTiming(relativeY - 4, { duration: 300 });
            holeW.value = withTiming(tw + 8, { duration: 300 });
            holeH.value = withTiming(th + 8, { duration: 300 });
          }
        });
      });
    } else {
      // Плавный сброс в центр, если таргета нет
      holeX.value = withTiming(SCREEN_WIDTH / 2);
      holeY.value = withTiming(SCREEN_HEIGHT / 2);
      holeW.value = withTiming(0);
      holeH.value = withTiming(0);
    }
  }, [currentStep, targets, steps, visible]);

  useEffect(() => {
    if (visible) {
      opacity.value = withTiming(1, { duration: 400 });

      const backHandler = BackHandler.addEventListener(
        "hardwareBackPress",
        () => {
          onFinish();
          return true;
        },
      );

      // Синхронизируем один раз при старте и по таймеру для страховки
      syncLayout();
      const timer = setTimeout(syncLayout, 300);

      const kbShow = Keyboard.addListener("keyboardDidShow", syncLayout);
      const kbHide = Keyboard.addListener("keyboardDidHide", syncLayout);

      return () => {
        backHandler.remove();
        kbShow.remove();
        kbHide.remove();
        clearTimeout(timer);
      };
    } else {
      opacity.value = withTiming(0, { duration: 300 });
    }
  }, [visible, syncLayout, onFinish, currentStep]); // Добавил currentStep, чтобы пересчитывать при смене шага

  const handleNext = () => {
    if (currentStep < steps.length - 1) nextStep();
    else onFinish();
  };

  const onTooltipLayout = (event: LayoutChangeEvent) => {
    setTooltipHeight(event.nativeEvent.layout.height);
  };

  const overlayStyle = useAnimatedStyle(() => ({ opacity: opacity.value }));

  // Единый стиль для всей маски
  const maskStyles = {
    top: useAnimatedStyle(() => ({
      height: Math.max(0, holeY.value),
      width: SCREEN_WIDTH,
    })),
    bottom: useAnimatedStyle(() => ({
      top: holeY.value + holeH.value,
      bottom: 0,
      width: SCREEN_WIDTH,
    })),
    left: useAnimatedStyle(() => ({
      top: holeY.value,
      height: holeH.value,
      width: Math.max(0, holeX.value),
    })),
    right: useAnimatedStyle(() => ({
      top: holeY.value,
      left: holeX.value + holeW.value,
      height: holeH.value,
      width: Math.max(0, SCREEN_WIDTH - (holeX.value + holeW.value)),
    })),
  };

  const tooltipStyle = useAnimatedStyle(() => {
    const isReady = holeW.value > 0;
    if (!isReady) {
      return {
        transform: [
          { translateY: SCREEN_HEIGHT / 3 },
          { scale: withTiming(0.9, { duration: 300 }) },
        ],
        opacity: withTiming(0.5, { duration: 300 }),
      };
    }

    const isBottomHalf = holeY.value > SCREEN_HEIGHT / 2;
    let targetY = isBottomHalf
      ? holeY.value - tooltipHeight - 12
      : holeY.value + holeH.value + 12;

    const safeY = Math.max(
      20,
      Math.min(targetY, SCREEN_HEIGHT - tooltipHeight - 20),
    );

    return {
      opacity: withTiming(1, { duration: 300 }),
      transform: [
        { translateY: withTiming(safeY, { duration: 300 }) },
        { scale: withTiming(1, { duration: 300 }) },
      ],
    };
  });

  if (!visible && opacity.value === 0) return null;

  const step = steps[currentStep];

  return (
    <Portal>
      <View
        ref={containerRef}
        style={StyleSheet.absoluteFill}
        pointerEvents="box-none"
      >
        <Animated.View
          style={[styles.container, overlayStyle]}
          pointerEvents={visible ? "box-none" : "none"}
        >
          {/* Маска */}
          <Animated.View style={[styles.backdrop, maskStyles.top]} />
          <Animated.View style={[styles.backdrop, maskStyles.bottom]} />
          <Animated.View style={[styles.backdrop, maskStyles.left]} />
          <Animated.View style={[styles.backdrop, maskStyles.right]} />

          {/* Контент */}
          <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
            <Animated.View
              onLayout={onTooltipLayout}
              style={[styles.tooltipContainer, tooltipStyle]}
            >
              <Surface
                elevation={5}
                style={[
                  styles.tooltip,
                  { backgroundColor: theme.colors.surface },
                ]}
              >
                <Text
                  variant="titleLarge"
                  style={{ color: theme.colors.primary, marginBottom: 8 }}
                >
                  {step?.title}
                </Text>
                <Text
                  variant="bodyLarge"
                  style={{
                    color: theme.colors.onSurfaceVariant,
                    marginBottom: 20,
                  }}
                >
                  {step?.description}
                </Text>
                <View style={styles.actions}>
                  <Text
                    variant="labelLarge"
                    style={{ color: theme.colors.onSurfaceVariant }}
                  >
                    {currentStep + 1} / {steps.length}
                  </Text>
                  <Button mode="contained" onPress={handleNext}>
                    {currentStep === steps.length - 1 ? "Finish" : "Next"}
                  </Button>
                </View>
              </Surface>
            </Animated.View>
          </View>
        </Animated.View>
      </View>
    </Portal>
  );
};

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 99999,
  },
  backdrop: {
    position: "absolute",
    backgroundColor: "rgba(0, 0, 0, 0.85)",
  },
  contentWrapper: {
    ...StyleSheet.absoluteFillObject,
  },
  tooltipContainer: {
    position: "absolute",
    left: 0,
    right: 0,
  },
  tooltip: {
    marginHorizontal: 20,
    padding: 24,
    borderRadius: 24,
  },
  actions: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
});
