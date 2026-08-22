import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

/**
 * Live seat-map subscription.
 *
 * Every hold, release, TTL expiry and booking is broadcast by
 * seat-hold-service to /topic/shows/{showId}/seatmap. Because the backend
 * fans out through Redis pub/sub first, this works correctly no matter which
 * service instance our socket landed on.
 *
 * Returns a disconnect function — always call it on unmount, or a customer
 * browsing several shows accumulates zombie subscriptions.
 */
export function subscribeSeatMap(showId, onEvent) {
  const client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client.subscribe(`/topic/shows/${showId}/seatmap`, (message) => {
        try {
          onEvent(JSON.parse(message.body));
        } catch (err) {
          console.warn('Bad seat-map frame', err);
        }
      });
    },
    onStompError: (frame) => console.error('STOMP error', frame.headers.message),
  });

  client.activate();
  return () => client.deactivate();
}
