import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

/**
 * Live seat-map subscription. UNCHANGED CONTRACT.
 *
 * Every hold, release, TTL expiry and booking is broadcast by
 * seat-hold-service to /topic/shows/{showId}/seatmap. Because the backend
 * fans out through Redis pub/sub first, this works correctly no matter which
 * service instance our socket landed on.
 *
 * `onStatus` is optional and purely cosmetic — it drives the "LIVE" pill in
 * the UI. Returns a disconnect function; always call it on unmount.
 */
export function subscribeSeatMap(showId, onEvent, onStatus = () => {}) {
  const client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      onStatus('live');
      client.subscribe(`/topic/shows/${showId}/seatmap`, (message) => {
        try {
          onEvent(JSON.parse(message.body));
        } catch (err) {
          console.warn('Bad seat-map frame', err);
        }
      });
    },
    onWebSocketClose: () => onStatus('reconnecting'),
    onStompError: (frame) => {
      onStatus('error');
      console.error('STOMP error', frame.headers.message);
    },
  });

  client.activate();
  return () => client.deactivate();
}
