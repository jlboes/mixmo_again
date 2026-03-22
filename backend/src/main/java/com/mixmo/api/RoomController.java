package com.mixmo.api;

import com.mixmo.common.ApiEnvelope;
import com.mixmo.common.VersionedResult;
import com.mixmo.room.RoomService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

  private final RoomService roomService;
  private final Clock clock;

  public RoomController(RoomService roomService, Clock clock) {
    this.roomService = roomService;
    this.clock = clock;
  }

  @PostMapping
  public ApiEnvelope<RestModels.RoomBootstrapData> createRoom(@RequestBody RestModels.CreateRoomRequest request) {
    return envelope(roomService.createRoom(request));
  }

  @PostMapping("/demo")
  public ApiEnvelope<RestModels.RoomBootstrapData> createDemoRoom(@RequestBody RestModels.CreateRoomRequest request) {
    return envelope(roomService.createDemoRoom(request));
  }

  @PostMapping("/{roomId}/join")
  public ApiEnvelope<RestModels.RoomBootstrapData> joinRoom(
      @PathVariable String roomId,
      @RequestBody RestModels.JoinRoomRequest request
  ) {
    return envelope(roomService.joinRoom(roomId, request));
  }

  @PostMapping("/{roomId}/leave")
  public ApiEnvelope<Map<String, Object>> leaveRoom(
      @PathVariable String roomId,
      @RequestBody RestModels.LeaveRoomRequest request
  ) {
    return envelope(roomService.leaveRoom(roomId, request));
  }

  @PostMapping("/{roomId}/start")
  public ApiEnvelope<Map<String, Object>> startRoom(
      @PathVariable String roomId,
      @RequestBody RestModels.StartRoomRequest request
  ) {
    return envelope(roomService.startRoom(roomId, request));
  }

  @GetMapping("/{roomId}")
  public ApiEnvelope<RestModels.RoomDto> getRoom(
      @PathVariable String roomId,
      @RequestParam String playerId,
      @RequestParam String sessionToken
  ) {
    return envelope(roomService.getRoom(roomId, playerId, sessionToken));
  }

  @GetMapping("/{roomId}/state")
  public ApiEnvelope<RestModels.GameSnapshotDto> getState(
      @PathVariable String roomId,
      @RequestParam String playerId,
      @RequestParam String sessionToken
  ) {
    return envelope(roomService.getState(roomId, playerId, sessionToken));
  }

  @GetMapping("/{roomId}/players/{targetPlayerId}/board")
  public ApiEnvelope<RestModels.PlayerBoardViewDto> getPlayerBoard(
      @PathVariable String roomId,
      @PathVariable String targetPlayerId,
      @RequestParam String playerId,
      @RequestParam String sessionToken
  ) {
    return envelope(roomService.getPlayerBoard(roomId, targetPlayerId, playerId, sessionToken));
  }

  @GetMapping("/{roomId}/chat")
  public ApiEnvelope<RestModels.ChatHistoryDto> getChatHistory(
      @PathVariable String roomId,
      @RequestParam String playerId,
      @RequestParam String sessionToken,
      @RequestParam(required = false) Integer limit
  ) {
    return envelope(roomService.getChatHistory(roomId, playerId, sessionToken, limit));
  }

  @PostMapping("/{roomId}/reconnect")
  public ApiEnvelope<RestModels.ReconnectData> reconnect(
      @PathVariable String roomId,
      @RequestBody RestModels.ReconnectRequest request
  ) {
    return envelope(roomService.reconnect(roomId, request));
  }

  @PostMapping("/{roomId}/mixmo/manual")
  public ApiEnvelope<Map<String, Object>> manualMixmo(
      @PathVariable String roomId,
      @RequestBody RestModels.ManualMixmoRequest request
  ) {
    return envelope(roomService.triggerManualMixmo(roomId, request));
  }

  @PostMapping("/{roomId}/mixmo/validate")
  public ApiEnvelope<RestModels.GridValidationResponse> validateMixmoGrid(
      @PathVariable String roomId,
      @RequestBody RestModels.GridValidationRequest request
  ) {
    return envelope(roomService.validateMixmoGrid(roomId, request));
  }

  private <T> ApiEnvelope<T> envelope(VersionedResult<T> result) {
    return new ApiEnvelope<>(
        UUID.randomUUID().toString(),
        Instant.now(clock),
        result.roomVersion(),
        result.data()
    );
  }
}
