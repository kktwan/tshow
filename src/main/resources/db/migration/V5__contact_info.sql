-- 문의 전화와 장소(공연장·전시장) 홈페이지. 소스가 주는데 지금까지 저장하지 않던 값이다.
ALTER TABLE source_record ADD COLUMN phone     TEXT;
ALTER TABLE source_record ADD COLUMN place_url TEXT;
ALTER TABLE event ADD COLUMN phone     TEXT;
ALTER TABLE event ADD COLUMN place_url TEXT;
