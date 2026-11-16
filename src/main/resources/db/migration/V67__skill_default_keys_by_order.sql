-- 스킬 기본 단축키: 대시(Space)는 그대로, 나머지는 해금 순서대로 앞의 10개만 1~9,0을 갖고 그 뒤 스킬은 기본 키가 없다(빈 문자열).
update skills set default_key = 'Digit1' where code = 'shield';
update skills set default_key = 'Digit2' where code = 'hook';
update skills set default_key = 'Digit3' where code = 'slow';
update skills set default_key = 'Digit4' where code = 'stealth';
update skills set default_key = 'Digit5' where code = 'repel';
update skills set default_key = 'Digit6' where code = 'fortify';
update skills set default_key = 'Digit7' where code = 'cleanse';
update skills set default_key = 'Digit8' where code = 'darkness';
update skills set default_key = 'Digit9' where code = 'root';
update skills set default_key = 'Digit0' where code = 'heal';
update skills set default_key = '' where code in ('poison', 'barrier', 'stun', 'silence', 'quake', 'confuse', 'fireball', 'amplify', 'absorb', 'berserk');
