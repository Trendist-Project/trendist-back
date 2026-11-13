-- 반발(shockwave)의 아이콘을 새 충격파(quake, 💥)와 겹치지 않게 🌀로 바꾼다. 처음 값 그대로인 행만 바꾼다(운영에서 고친 아이콘은 건드리지 않는다).
update skills set icon = '🌀' where code = 'shockwave' and icon = '💥';
