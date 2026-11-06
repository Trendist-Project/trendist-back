-- 같은 상대 연속 충돌 쿨다운은 설정에서 빼고 코드의 고정값(0.5초)으로 둔다. 관리자 화면에 남지 않게 행을 지운다
delete from settings where name = 'collision_cooldown_seconds';
