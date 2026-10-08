-- 소스가 주는 상세·홈페이지 링크 (문화정보원 url, TourAPI homepage). 예매 링크(ticket_links)와 구분한다.
ALTER TABLE source_record ADD COLUMN info_url TEXT;
