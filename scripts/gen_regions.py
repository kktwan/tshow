"""한국관광공사 TourAPI 법정동 코드표(ldongCode2)로 src/main/resources/taxonomy/regions.csv 를 만든다.
지역 코드표를 손으로 쓰지 않고 공식 API에서 받아 생성한다. 행정구역이 바뀌면 이 스크립트를 다시 실행한다.
사용: python scripts/gen_regions.py   (프로젝트 루트의 .env 에 TOURAPI_API_KEY 필요)
"""
import csv, json, pathlib, urllib.parse, urllib.request

root = pathlib.Path(__file__).resolve().parent.parent
env = {}
for line in (root / '.env').read_text(encoding='utf-8').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        k, v = line.split('=', 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
key = env['TOURAPI_API_KEY']
key = key if '%' in key else urllib.parse.quote(key, safe='')
url = ("https://apis.data.go.kr/B551011/KorService2/ldongCode2?serviceKey=" + key +
       "&MobileOS=ETC&MobileApp=tshow&_type=json&numOfRows=1000&pageNo=1&lDongListYn=Y")
body = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'}), timeout=30).read()
items = json.loads(body)['response']['body']['items']['item']
out = root / 'src/main/resources/taxonomy/regions.csv'
with out.open('w', encoding='utf-8', newline='') as f:
    w = csv.writer(f, lineterminator='\n')
    w.writerow(['sido_code', 'sido_name', 'sigungu_code', 'sigungu_name'])
    for it in items:
        w.writerow([it['lDongRegnCd'], it['lDongRegnNm'], it['lDongSignguCd'], it['lDongSignguNm']])
print(f'{len(items)}행 저장: {out}')
