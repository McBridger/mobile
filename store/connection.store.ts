import { AppConfig } from "@/app.config";
import { BleConnector, Received } from "@/specs/NativeBleConnector";
import { LogEntry } from "@/utils/recorder";
import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import { setError } from ".";

type BleStatus = "disconnected" | "connecting" | "connected" | "disconnecting";
export type Item = { id: string; type: string; content: string; time: number };

const defaultState = {
  status: "disconnected" as BleStatus,
  address: null,
  name: null,
};


interface BleState {
  status: BleStatus;
  address: string | null;
  name: string | null;
  items: Map<string, Item>;

  connect: (address: string, name: string, extra: AppConfig["extra"]) => void;
  disconnect: () => void;
  addRecorded: (entries: LogEntry[]) => void;
}

export const useConnector = create<BleState>()(
  subscribeWithSelector((set, get) => ({
    ...defaultState,
    items: new Map<string, Item>(),

    connect: (address: string, name: string, extra: AppConfig["extra"]) => {
      if (get().status !== "disconnected") return;
      set({ status: "connecting", address, name });

      initConnection(address, name, extra).catch((err) => {
        set(defaultState);
        setError(err);
      });
    },
    disconnect: () => {
      if (get().status !== "connected") return;

      set({ status: "disconnecting" });

      BleConnector.disconnect().catch((err) => {
        set(defaultState);
        setError(err);
      });
    },
    addRecorded: (entries) => {
      set((prevState) => {
        const newItems = new Map(prevState.items);

        entries.forEach((entry) => {
          if (newItems.has(entry.id)) return;

          newItems.set(entry.id, {
            time: entry.time,
            id: entry.id,
            type: "received",
            content: entry.value,
          });
        })

        return {
            ...prevState,
            items: newItems,
          }
      })
    }
  }))
);

async function initConnection(address: string, name: string, extra: AppConfig["extra"]) {
  const isConnected = await BleConnector.isConnected();
  if (isConnected)
    return useConnector.setState({ status: "connected", address, name });

  const {
    BRIDGER_SERVICE_UUID,
    CHARACTERISTIC_UUID,
  } = extra;

  await BleConnector.setup(
    BRIDGER_SERVICE_UUID,
    CHARACTERISTIC_UUID,
  );

  await BleConnector.connect(address);
}

export const handleConnected = () => {
  useConnector.setState((prevState) => ({ ...prevState, status: "connected" }));
};

export const handleDisconnected = () => {
  useConnector.setState(defaultState);
};

export const handleConnectionFailed = ({ device, name, reason }: { device: string; name?: string; reason: string }) => {
  useConnector.setState(defaultState);
  const deviceIdentifier = name || device;
  setError(new Error(`Failed to connect to ${deviceIdentifier}: ${reason}`));
};

export const handleReceived = (data: Received) => {
  useConnector.setState((prev) => {
    const newItems = new Map(prev.items);

    newItems.set(data.id, {
      id: data.id,
      type: "received",
      content: data.value,
      time: Date.now()
    })

    return {
      ...prev,
      items: newItems
    }
  });
};

useConnector.subscribe((state, prevState) => {
  console.debug("[BleStore] State changed:", JSON.stringify(state, null, 2));
});
