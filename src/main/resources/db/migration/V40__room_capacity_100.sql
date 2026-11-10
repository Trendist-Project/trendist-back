-- 방 정원을 1000명에서 100명으로 줄인다(맵 크기 계산의 기준 인원도 100명).
UPDATE countries SET max_room_size = 100 WHERE max_room_size = 1000;
