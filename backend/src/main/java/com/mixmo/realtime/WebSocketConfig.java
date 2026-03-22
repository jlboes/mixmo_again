package com.mixmo.realtime;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final GameWebSocketHandler gameWebSocketHandler;
  private final String[] allowedOrigins;

  public WebSocketConfig(
      GameWebSocketHandler gameWebSocketHandler,
      @Value("${mixmo.cors.allowed-origins}") String allowedOrigins
  ) {
    this.gameWebSocketHandler = gameWebSocketHandler;
    this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new);
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(gameWebSocketHandler, "/ws").setAllowedOrigins(allowedOrigins);
  }
}

