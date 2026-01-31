import { useTutorialStore } from "@/hooks/useTutorialStore";
import React, { useEffect, useRef } from "react";
import { View, ViewProps } from "react-native";

interface Props extends ViewProps {
  name: string;
  children: React.ReactNode;
}

/**
 * Wrapper for ref elements - registers ref in store
 */
export const TutorialTarget = ({ name, children, style, ...props }: Props) => {
  const registerTarget = useTutorialStore((state) => state.registerTarget);
  const unregisterTarget = useTutorialStore((state) => state.unregisterTarget);
  const ref = useRef<View>(null);

  useEffect(() => {
    registerTarget(name, ref);
    return () => unregisterTarget(name);
  }, [name, registerTarget, unregisterTarget]);

  return (
    <View ref={ref} collapsable={false} style={style} {...props}>
      {children}
    </View>
  );
};
