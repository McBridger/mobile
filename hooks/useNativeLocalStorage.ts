import NativeLocalStorage, { KeyValuePair } from "@/specs/NativeLocalStorage";
import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, EventSubscription } from "react-native";

export function useNativeLocalStorage() {
  const [last, setLast] = useState<KeyValuePair | null>(null);
  const listenerSubscription = useRef<null | EventSubscription>(null);

  useEffect(() => {
    listenerSubscription.current = NativeLocalStorage?.onKeyAdded(setLast);

    return () => {
      listenerSubscription.current?.remove();
      listenerSubscription.current = null;
    };
  }, []);

  const set = useCallback((key: string, value: string = "") => {
    if (key == null) return Alert.alert("Please enter a key");

    NativeLocalStorage?.setItem(value, key);
  }, []);

  const clearAll = useCallback(() => {
    NativeLocalStorage?.clear();
  }, []);

  const remove = useCallback((key: string) => {
    if (key == null) return Alert.alert("Please enter a key");

    NativeLocalStorage?.removeItem(key);
  }, []);

  const get = useCallback((key?: string) => {
    if (key == null) {
      Alert.alert("Please enter a key");
      return;
    }

    return NativeLocalStorage?.getItem(key) ?? undefined;
  }, []);

  return {
    last,
    set,
    clearAll,
    remove,
    get,
  };
}
