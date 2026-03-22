alter table game_room
  add column next_stale_warning_at timestamp with time zone,
  add column automatic_mixmo_at timestamp with time zone;

create index ix_game_room_stale_warning on game_room(next_stale_warning_at);
create index ix_game_room_automatic_mixmo on game_room(automatic_mixmo_at);
