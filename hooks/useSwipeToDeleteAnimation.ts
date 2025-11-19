import { useCallback } from "react";
import { LayoutChangeEvent } from "react-native";
import {
    runOnJS,
    useAnimatedReaction,
    useAnimatedStyle,
    useSharedValue,
    withTiming,
} from "react-native-reanimated";

type UseSwipeToDeleteAnimationProps = {
  onAnimationEnd: () => void;
};

/**
* Hook encapsulating the animation logic for an element's disappearance.
* @param {UseSwipeToDeleteAnimationProps} props - Props, including the onAnimationEnd callback.
* @returns A set of animated styles and handlers to apply to the component.
*/
export const useSwipeToDeleteAnimation = ({ onAnimationEnd }: UseSwipeToDeleteAnimationProps) => {
  const height = useSharedValue<number | undefined>(undefined);
  const isMarkedForRemoval = useSharedValue(false);

// Style for the outer container (controls the height)
  const containerStyle = useAnimatedStyle(() => ({
    height: height.value,
    overflow: "hidden",
  }));

// Style for inner content (controls transparency)
  const contentStyle = useAnimatedStyle(() => ({
    opacity: withTiming(isMarkedForRemoval.value ? 0 : 1, { duration: 200 }),
  }));

  const handleAnimationEnd = useCallback(() => {
    onAnimationEnd();
  }, [onAnimationEnd]);

  useAnimatedReaction(
    () => isMarkedForRemoval.value,
    (shouldRemove, previousValue) => {
      if (shouldRemove && !previousValue) {
        height.value = withTiming(0, { duration: 300, delay: 50 }, (isFinished) => {
          if (isFinished) {
            runOnJS(handleAnimationEnd)();
          }
        });
      }
    },
    [handleAnimationEnd]
  );

  const triggerDelete = useCallback(() => {
    isMarkedForRemoval.value = true;
  }, []);

// Handler for measuring height
  const onLayoutHandler = useCallback((event: LayoutChangeEvent) => {
    if (height.value === undefined) {
      height.value = event.nativeEvent.layout.height;
    }
  }, []);

  return {
    containerStyle,
    contentStyle,
    isMarkedForRemoval,
    triggerDelete,
    onLayoutHandler,
  };
};