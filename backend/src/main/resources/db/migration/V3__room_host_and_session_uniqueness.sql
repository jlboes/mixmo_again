alter table game_room
  add column host_player_id varchar(64);

update game_room room
set host_player_id = player.id
from game_player player
where player.room_id = room.id
  and player.seat_order = 1;

alter table game_room
  alter column host_player_id set not null;

create unique index ux_game_player_room_session on game_player(room_id, session_token);
