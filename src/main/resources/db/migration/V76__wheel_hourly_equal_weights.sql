-- 돌림판: 하루 한 번 → 마지막으로 돌린 뒤 일정 시간(설정 wheel_cooldown_minutes, 기본 60분)이 지나면 다시 돌릴 수 있게 바꾼다(4.37).
-- 같은 날 두 번 돌리지 못하게 하던 유일 키(player_id, spin_date)를 없애고, 마지막 돌림을 빨리 찾도록 (player_id, created_at) 색인을 둔다
alter table wheel_spins drop index uk_wheel_spins_player_date;
create index ix_wheel_spins_player_created on wheel_spins (player_id, created_at);

-- 칸마다 뽑힐 확률을 모두 같게(비중 10씩, 10칸이라 10%씩). 시더가 처음 넣은 값 그대로인 행만 바꾼다(운영에서 고친 값은 건드리지 않는다)
update settings set value = '10' where name = 'wheel_weight_1' and value = '22';
update settings set value = '10' where name = 'wheel_weight_2' and value = '20';
update settings set value = '10' where name = 'wheel_weight_3' and value = '16';
update settings set value = '10' where name = 'wheel_weight_4' and value = '13';
update settings set value = '10' where name = 'wheel_weight_5' and value = '10';
update settings set value = '10' where name = 'wheel_weight_6' and value = '7';
update settings set value = '10' where name = 'wheel_weight_8' and value = '5';
update settings set value = '10' where name = 'wheel_weight_10' and value = '4';
update settings set value = '10' where name = 'wheel_weight_15' and value = '2';
update settings set value = '10' where name = 'wheel_weight_20' and value = '1';
