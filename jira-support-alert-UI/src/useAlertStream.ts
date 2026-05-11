import { useCallback, useEffect, useRef, useState } from "react";
import type { AlertPayload } from "./api";

const MAX = 50;

export function useAlertStream(onAlert: (a: AlertPayload) => void) {
  const [connected, setConnected] = useState(false);
  const onAlertRef = useRef(onAlert);
  onAlertRef.current = onAlert;

  const connect = useCallback(() => {
    const es = new EventSource("/alerts/stream");

    es.addEventListener("open", () => setConnected(true));
    es.addEventListener("error", () => setConnected(false));

    es.addEventListener("alert", (ev) => {
      try {
        const data = JSON.parse((ev as MessageEvent).data) as AlertPayload;
        onAlertRef.current(data);
      } catch {
        /* ignore */
      }
    });

    return () => {
      es.close();
      setConnected(false);
    };
  }, []);

  useEffect(() => {
    return connect();
  }, [connect]);

  return { connected };
}

export function useAlertHistory() {
  const [items, setItems] = useState<AlertPayload[]>([]);

  const push = useCallback((a: AlertPayload) => {
    setItems((prev) => [a, ...prev].slice(0, MAX));
  }, []);

  const clear = useCallback(() => setItems([]), []);

  return { items, push, clear };
}
