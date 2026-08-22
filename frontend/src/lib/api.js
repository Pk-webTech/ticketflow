/**
 * Thin fetch wrapper.
 *
 * Everything goes through the Nginx gateway on a single origin, so the client
 * never needs per-service hostnames — that routing lives in nginx.conf and can
 * change (scale out, move a service) without touching the frontend.
 */
const TOKEN_KEY = 'tf_access_token';
const USER_KEY = 'tf_user';

export const auth = {
  get token() {
    return localStorage.getItem(TOKEN_KEY);
  },
  get user() {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY));
    } catch {
      return null;
    }
  },
  save(token, user) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
};

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`;

  const res = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (res.status === 401) {
    auth.clear();
    throw new ApiError(401, 'Your session has expired. Please sign in again.');
  }

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const payload = await res.json();
      if (payload?.message) message = payload.message;
    } catch { /* non-JSON error body */ }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  get: (p) => request('GET', p),
  post: (p, b) => request('POST', p, b),
  patch: (p, b) => request('PATCH', p, b),
  put: (p, b) => request('PUT', p, b),
  del: (p, b) => request('DELETE', p, b),
};

// ---- endpoint helpers -------------------------------------------------

export const Auth = {
  login: (email, password) => api.post('/api/auth/login', { email, password }),
  register: (payload) => api.post('/api/auth/register', payload),
};

export const Events = {
  search: (params) => api.get('/api/events/search?' + new URLSearchParams(params)),
  list: () => api.get('/api/events'),
  get: (id) => api.get(`/api/events/${id}`),
  shows: (eventId) => api.get(`/api/events/${eventId}/shows`),
  mine: () => api.get('/api/events/mine'),
  show: (showId) => api.get(`/api/shows/${showId}`),
  create: (payload) => api.post('/api/events', payload),
  createShow: (eventId, payload) => api.post(`/api/events/${eventId}/shows`, payload),
};

export const Venues = {
  list: () => api.get('/api/venues'),
  get: (id) => api.get(`/api/venues/${id}`),
  create: (payload) => api.post('/api/venues', payload),
  categories: (venueId) => api.get(`/api/venues/${venueId}/categories`),
  createCategory: (venueId, payload) => api.post(`/api/venues/${venueId}/categories`, payload),
  seats: (venueId) => api.get(`/api/venues/${venueId}/seats`),
  createSeats: (venueId, payload) => api.post(`/api/venues/${venueId}/seats`, payload),
};

export const Seats = {
  map: (showId) => api.get(`/api/seats/shows/${showId}/map`),
  hold: (showId, seatIds) => api.post(`/api/seats/shows/${showId}/hold`, { seatIds }),
  // seat-hold-service's release route is nested under the show and expects the
  // seat list in the body, so a DELETE carries a payload here.
  release: (showId, holdId, seatIds) =>
    api.del(`/api/seats/shows/${showId}/holds/${holdId}`, seatIds),
};

export const Bookings = {
  confirm: (payload) => api.post('/api/bookings', payload),
  mine: () => api.get('/api/bookings/me'),
  get: (id) => api.get(`/api/bookings/${id}`),
  cancel: (id) => api.patch(`/api/bookings/${id}/cancel`),
  bookedSeats: (showId) => api.get(`/api/bookings/shows/${showId}/booked-seats`),
  showSummary: (showId) => api.get(`/api/bookings/summary/shows/${showId}`),
  eventSummary: (eventId) => api.get(`/api/bookings/summary/events/${eventId}`),
};

export const Waitlist = {
  join: (payload) => api.post('/api/waitlist', payload),
  mine: () => api.get('/api/waitlist/me'),
  leave: (id) => api.del(`/api/waitlist/${id}`),
  viewOffer: (token) => api.get(`/api/waitlist/offers/token/${token}`),
  acceptOffer: (token) => api.post(`/api/waitlist/offers/token/${token}/accept`),
};
