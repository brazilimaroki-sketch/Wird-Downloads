#!/usr/bin/env python3
"""Export verified, dated Ministry tables as static JSON. Python 3.10+, stdlib only."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import ssl
import sys
import tempfile
import time
import urllib.request
from html import unescape
from html.parser import HTMLParser

ROOT = Path(__file__).resolve().parent
KEYS = ('Fajr', 'Sunrise', 'Dhuhr', 'Asr', 'Maghrib', 'Isha')
MONTHS = ['يناير', 'فبراير', 'مارس', 'أبريل|ابريل', 'ماي|مايو', 'يونيو|يونية',
          'يوليوز|يوليو|يولية', 'غشت|أغسطس|اغسطس', 'شتنبر|سبتمبر', 'أكتوبر|اكتوبر',
          'نونبر|نوفمبر', 'دجنبر|ديسمبر']
WEEK = ['الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت', 'الأحد']
HOSTS = ('www.habous.gov.ma', 'habous.gov.ma')
PATHS = ('/prieres/horaire_hijri_2.php', '/prieres/index.php')


def clean(value):
    value = re.sub(r'(?is)<script\b.*?</script>|<style\b.*?</style>', ' ', value)
    value = unescape(re.sub(r'<[^>]+>', ' ', value))
    value = value.translate(str.maketrans('٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹', '01234567890123456789'))
    value = re.sub(r'[\u200e\u200f\u202a-\u202e]', ' ', value)
    return ' '.join(value.split())


def norm(value):
    value = re.sub(r'[\u064b-\u065fـ]', '', clean(value))
    return re.sub(r'\s+', '', value.translate(str.maketrans('أإآىة', 'ااايه')))


def checked_times(values):
    if len(values) != 6:
        raise ValueError('six prayer times required')
    result, previous = {}, -1
    for key, value in zip(KEYS, values):
        if not re.fullmatch(r'(?:[01]?\d|2[0-3]):[0-5]\d', value):
            raise ValueError('invalid prayer clock')
        h, m = map(int, value.split(':'))
        minute = h * 60 + m
        if minute <= previous:
            raise ValueError('prayer clocks out of order')
        result[key], previous = f'{h:02}:{m:02}', minute
    return result


class Rows(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.rows, self.row, self.cell = [], None, None

    def handle_starttag(self, tag, attrs):
        if tag == 'tr':
            self.row = []
        elif tag in ('td', 'th') and self.row is not None:
            self.cell = []

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)

    def handle_endtag(self, tag):
        if tag in ('td', 'th') and self.cell is not None:
            self.row.append(clean(' '.join(self.cell)))
            self.cell = None
        elif tag == 'tr' and self.row is not None:
            self.rows.append(self.row)
            self.row, self.cell = None, None


def parse_month(html, city, now=None):
    now = now or dt.datetime.now(dt.timezone.utc)
    selected = []
    for attrs, label in re.findall(r'(?is)<option\b([^>]*)>(.*?)</option>', html):
        if re.search(r'\bselected\b', attrs, re.I):
            match = re.search(r'''\bvalue\s*=\s*['"]([^'"]+)['"]''', attrs, re.I)
            selected.append(match.group(1) if match else '')
    cid = city['id']
    if selected:
        if not any(v == str(cid) or re.search(r'[?&]ville=' + str(cid) + r'(?:&|$)', unescape(v)) for v in selected):
            raise ValueError('wrong selected city')
    else:
        heading = re.search(r'(?is)مواقيت\s+الصلاة\s+لمدينة\s*(.{1,180}?)<(?:/h|/div|/p|br)', html)
        if not heading or norm(heading.group(1)) != norm(city['name']):
            raise ValueError('city identity missing')
    table = re.search(r'''(?is)<table\b[^>]*\bid\s*=\s*['"]horaire['"][^>]*>(.*?)</table>''', html)
    body = table.group(1) if table else html
    parser = Rows()
    parser.feed(body)
    data = []
    for row in parser.rows:
        # Do not silently omit malformed prayer rows, including broken Fajr.
        clocks = sum(bool(re.fullmatch(r'\d{1,2}:\d{2}', x)) for x in row)
        if clocks >= 4:
            if len(row) != 9:
                raise ValueError('unexpected number of table cells')
            checked_times(row[3:])
            data.append(row)
    if len(data) == 30 and norm(data[-1][1]) in {norm('حسب رؤية الهلال'), norm('حسب نتيجة المراقبة')}:
        data.pop()
    if len(data) not in (29, 30) or data[0][1] != '1':
        raise ValueError('incomplete monthly table')
    prefix = clean(body.split(data[0][3], 1)[0])
    positions = []
    for index, pattern in enumerate(MONTHS, 1):
        match = re.search(r'(?<![\u0600-\u06ff])(?:' + pattern + r')(?![\u0600-\u06ff])', prefix)
        if match:
            positions.append((match.start(), index))
    months = [m for _, m in sorted(positions)]
    if not 1 <= len(months) <= 2:
        raise ValueError('printed Gregorian months missing')
    years = {int(y) for y in re.findall(r'(?<!\d)20\d{2}(?!\d)', clean(html.split(data[0][3], 1)[0]))}
    if not years:
        raise ValueError('printed Gregorian year missing')
    matches = []
    for year in sorted(years | {y - 1 for y in years}):
        try:
            day = dt.date(year, months[0], int(data[0][2]))
            days, seen_months, seen_years = [], [], set()
            for hd, row in enumerate(data, 1):
                if int(row[1]) != hd or int(row[2]) != day.day or norm(row[0]) != norm(WEEK[day.weekday()]):
                    raise ValueError('date/weekday mismatch')
                if day.month not in seen_months:
                    seen_months.append(day.month)
                seen_years.add(day.year)
                days.append({'cityId': cid, 'city': city['name'], 'dateKey': day.isoformat(),
                             'source': 'official', 'times': checked_times(row[3:])})
                day += dt.timedelta(days=1)
            if seen_months != months or not seen_years <= years:
                continue
            first, last = map(dt.date.fromisoformat, (days[0]['dateKey'], days[-1]['dateKey']))
            if last < now.date() - dt.timedelta(days=3) or first > now.date() + dt.timedelta(days=35):
                continue
            matches.append({'schema': 1, 'cityId': cid, 'city': city['name'], 'source': 'habous.gov.ma',
                            'timezone': 'UTC', 'fetchedAt': int(now.timestamp() * 1000),
                            'firstDate': first.isoformat(), 'lastDate': last.isoformat(), 'days': days})
        except (ValueError, IndexError):
            continue
    if len(matches) != 1:
        raise ValueError('ambiguous, expired or inconsistent month')
    return matches[0]


class MinistryRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        from urllib.parse import urlsplit
        target = urlsplit(newurl)
        if target.scheme != 'https' or target.hostname not in HOSTS:
            raise ValueError('redirect outside Ministry HTTPS hosts')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def fetch_month(city, diagnostics=None):
    context = ssl.create_default_context()
    for name, expected in [('sectigo_root_r46', '7bb647a62aeeac88bf257aa522d01ffea395e0ab45c73f93f65654ec38f25a06'),
                           ('sectigo_dv_r36', '8c54c334b66ba4e426772af4a3f9136c19a1aec729fdb28c535c07a5a4ef22e0')]:
        path = ROOT / 'certs' / (name + '.pem')
        if hashlib.sha256(ssl.PEM_cert_to_DER_cert(path.read_text())).hexdigest() != expected:
            raise ValueError('CA asset fingerprint mismatch')
        context.load_verify_locations(cafile=str(path))
    opener = urllib.request.build_opener(MinistryRedirect(), urllib.request.HTTPSHandler(context=context))
    failures = []
    for host in HOSTS:
        for path in PATHS:
            url = f'https://{host}{path}?ville={city["id"]}'
            try:
                request = urllib.request.Request(url, headers={'User-Agent': 'WirdPrayerTableSync/1.0', 'Accept-Language': 'ar-MA'})
                with opener.open(request, timeout=12) as response:
                    raw = response.read(524289)
                    if len(raw) > 524288:
                        raise ValueError('page exceeds size limit')
                    html = raw.decode(response.headers.get_content_charset() or 'utf-8')
                if diagnostics is not None:
                    diagnostics.mkdir(parents=True, exist_ok=True)
                    (diagnostics / f'{city["id"]}_{Path(path).stem}.html').write_text(html, encoding='utf-8')
                print(f'city={city["id"]} received {len(raw)} bytes from {url}', file=sys.stderr)
                result = parse_month(html, city)
                result['sourceUrl'] = url
                return result
            except Exception as error:
                failures.append(type(error).__name__ + ': ' + str(error)[:140])
    raise RuntimeError('; '.join(failures))


def atomic_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as handle:
            temp = handle.name
            json.dump(data, handle, ensure_ascii=False, separators=(',', ':'))
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp, path)
    finally:
        if temp and os.path.exists(temp):
            os.unlink(temp)


def synchronize(cities, output, fetcher=fetch_month):
    failures, results = 0, []
    for city in cities:
        try:
            result = fetcher(city)
            atomic_json(output / f'{city["id"]}.json', result)
            results.append({'cityId': city['id'], 'success': True})
            failures = 0
        except Exception as error:
            # Existing data stays untouched. No computed data is labelled official.
            results.append({'cityId': city['id'], 'success': False, 'error': str(error)[:600]})
            failures += 1
            if failures >= 2:
                break
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--city', type=int, action='append', help='Ministry city ID; repeat or omit for all catalog IDs')
    parser.add_argument('--diagnostics', type=Path, help='Optional raw Ministry HTML outside the public JSON directory')
    parser.add_argument('--output', type=Path, default=ROOT / 'public/api/v1')
    args = parser.parse_args()
    catalog = json.loads((ROOT / 'cities.json').read_text(encoding='utf-8'))
    requested = set(args.city or [x['id'] for x in catalog])
    if requested - {x['id'] for x in catalog}:
        parser.error('unknown Ministry city ID')
    selected = [x for x in catalog if x['id'] in requested]
    selected.sort(key=lambda x: x['id'] != 158)  # Existing reference city first; every requested ID remains queued.
    results = synchronize(selected, args.output, lambda city: fetch_month(city, args.diagnostics))
    atomic_json(args.output / 'catalog.json', {'schema': 1, 'cities': catalog})
    atomic_json(args.output / 'sync-status.json', {'schema': 1, 'checkedAt': int(time.time() * 1000),
                'requested': len(selected), 'attempted': len(results), 'results': results})
    print(json.dumps({'requested': len(selected), 'attempted': len(results), 'saved': sum(x['success'] for x in results)}))
    return 0 if len(results) == len(selected) and all(x['success'] for x in results) else 1


if __name__ == '__main__':
    raise SystemExit(main())
