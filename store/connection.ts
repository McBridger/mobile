import { AppConfig } from "@/app.config";
import { BleConnector } from "@/specs/NativeBleConnector";
import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import { setError } from ".";

type BleStatus = "disconnected" | "connecting" | "connected" | "disconnecting";
type Item = { id: string; type: string; content: string; timestamp: string };

interface BleState {
  status: BleStatus;
  address: string | null;
  items: Item[];

  connect: (address: string, extra: AppConfig["extra"]) => void;
  disconnect: () => void;
}

export const useConnector = create<BleState>()(
  subscribeWithSelector((set, get) => ({
    status: "disconnected",
    address: null,
    items: [],

    connect: (address: string, extra: AppConfig["extra"]) => {
      if (get().status !== "disconnected") return;

      connect(address, extra).catch((err) => {
        useConnector.setState({ status: "disconnected" });
        setError(err);
      });
    },
    disconnect: () => {
      if (get().status !== "connected") return;

      set({ status: "disconnecting" });

      BleConnector.disconnect().catch((err) => {
        useConnector.setState({ status: "disconnected" });
        setError(err);
      });
    },
  }))
);

async function connect(address: string, extra: AppConfig["extra"]) {
  const isConnected = await BleConnector.isConnected();
  if (isConnected)
    return useConnector.setState({ status: "connected", address });

  const {
    BRIDGER_SERVICE_UUID,
    WRITE_CHARACTERISTIC_UUID,
    NOTIFY_CHARACTERISTIC_UUID,
  } = extra;

  await BleConnector.setup(
    BRIDGER_SERVICE_UUID,
    WRITE_CHARACTERISTIC_UUID,
    NOTIFY_CHARACTERISTIC_UUID
  );

  await BleConnector.connect(address);
}

BleConnector.onConnected(() => {
  useConnector.setState((prevState) => ({ ...prevState, status: "connected" }));
});

BleConnector.onDisconnected(() => {
  useConnector.setState({ status: "disconnected", address: null });
});

BleConnector.onConnectionFailed(({ device, reason }) => {
  useConnector.setState({
    status: "disconnected",
    address: null,
  });
  setError(new Error(`Failed to connect to ${device}: ${reason}`));
});

BleConnector.onReceived((data) => {
  useConnector.setState((prev) => ({
    ...prev,
    items: prev.items.concat({
      id: `${prev.items.length}`,
      type: "received",
      content: data,
      timestamp: new Date().toLocaleString(),
    }),
  }));
});

useConnector.subscribe((state, prevState) => {
  console.debug("[BleStore] State changed:", JSON.stringify(state, null, 2));
});
