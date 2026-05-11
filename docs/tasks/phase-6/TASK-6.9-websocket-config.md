# TASK-6.9 — WebSocket Configuration

## Overview

Create the Spring WebSocket configuration that enables STOMP messaging over SockJS with JWT-based authentication on the WebSocket handshake channel.

## Requirements

- Lives in `infrastructure/config/`.
- `@EnableWebSocketMessageBroker` activates the message broker.
- JWT authentication must be wired into the inbound channel so `@MessageMapping` methods can access `Principal` (the authenticated user).

## File Locations

```
backend/src/main/java/com/instagram/infrastructure/config/WebSocketConfig.java
backend/src/main/java/com/instagram/infrastructure/config/WebSocketAuthChannelInterceptor.java
```

---

## Checklist

### `WebSocketConfig.java`

- [ ] `@Configuration @EnableWebSocketMessageBroker`
- [ ] Implements `WebSocketMessageBrokerConfigurer`
- [ ] `configureMessageBroker(MessageBrokerRegistry)`:
  - Enable simple in-memory broker on `/topic` and `/user` destinations.
  - Set application destination prefix to `/app`.
  - Set user destination prefix to `/user` (used for `convertAndSendToUser`).
- [ ] `registerStompEndpoints(StompEndpointRegistry)`:
  - Register `/ws` endpoint.
  - Allow all origins (or configure from `application.properties`).
  - Enable SockJS fallback.
- [ ] `configureClientInboundChannel(ChannelRegistration)`:
  - Add `WebSocketAuthChannelInterceptor` to intercept and authenticate STOMP `CONNECT` frames.

### `WebSocketAuthChannelInterceptor.java`

- [ ] `@Component` + implements `ChannelInterceptor`
- [ ] Constructor injects: the existing `JwtTokenProvider` (or equivalent JWT utility class already in `infrastructure/security/`).
- [ ] Override `preSend(Message<?>, MessageChannel)`:
  - Only process `StompCommand.CONNECT` frames.
  - Extract `Authorization` header from `StompHeaderAccessor`.
  - If header present and starts with `"Bearer "`, extract the token, validate it, and set the authenticated `UsernamePasswordAuthenticationToken` as the `user` on the accessor.
  - Return the (possibly modified) message.

## Notes

- The JWT utility class (`JwtTokenProvider` or similar) already exists from Phase 1 — do not rewrite it.
- `UsernamePasswordAuthenticationToken` with a non-null `principal` and empty authorities is sufficient. The `principal` name should be the user's UUID string so that `@MessageMapping` methods can call `principal.getName()` to get the sender's ID.
- SockJS fallback is required for environments where native WebSockets are blocked (e.g., some corporate proxies).
- Cross-origin: configure allowed origins to match the frontend URL (`http://localhost:5173`). Use `application.properties` to avoid hardcoding.
