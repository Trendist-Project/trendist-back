-- 콤보 칭호 설명: 보스를 쳐도 콤보가 쌓이므로 "빼앗았다" 대신 "콤보를 N 달성했어요"로(한국어·영어)
update titles set description = '콤보를 3 달성했어요', description_en = 'Reached a combo of 3' where name = '연속 타격';
update titles set description = '콤보를 5 달성해 불꽃을 붙였어요', description_en = 'Reached a combo of 5 and lit the flame' where name = '불꽃 점화';
update titles set description = '콤보를 10 달성했어요', description_en = 'Reached a combo of 10' where name = '연쇄 반응';
update titles set description = '콤보를 20 달성했어요', description_en = 'Reached a combo of 20' where name = '폭주 기관차';
update titles set description = '콤보를 40 달성했어요', description_en = 'Reached a combo of 40' where name = '멈추지 않는 불꽃';
update titles set description = '콤보를 100 달성했어요', description_en = 'Reached a combo of 100' where name = '100 콤보';
