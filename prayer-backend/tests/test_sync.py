import datetime as dt
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('sync', ROOT / 'sync.py')
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)
NOW = dt.datetime(2026, 10, 16, tzinfo=dt.timezone.utc)
CITY = {'id': 158, 'name': 'بوجدور'}


def fixture(city=CITY, start='2026-10-13', heading='أكتوبر / نونبر 2026', count=30, conditional=False):
    day = dt.date.fromisoformat(start)
    html = f'<option value="{city["id"]}" selected>{city["name"]}</option><h2>{heading}</h2><table id="horaire"><tr><th>{heading}</th></tr>'
    for hd in range(1, count + 1):
        label = 'حسب رؤية الهلال' if conditional and hd == 30 else str(hd)
        # Synthetic clock values, not downloaded official data.
        html += f'<tr><td>{sync.WEEK[day.weekday()]}</td><td>{label}</td><td>{day.day}</td><td>05:35</td><td>06:54</td><td>12:49</td><td>16:02</td><td>18:34</td><td>19:43</td></tr>'
        day += dt.timedelta(days=1)
    return html + '</table>'


class SyncTest(unittest.TestCase):
    def test_dates_and_conditional_row(self):
        full = sync.parse_month(fixture(), CITY, NOW)
        self.assertEqual(full['firstDate'], '2026-10-13')
        self.assertEqual(full['lastDate'], '2026-11-11')
        confirmed = sync.parse_month(fixture(conditional=True), CITY, NOW)
        self.assertEqual(len(confirmed['days']), 29)
        self.assertEqual(confirmed['lastDate'], '2026-11-10')
        self.assertEqual(confirmed['days'][2]['times']['Isha'], '19:43')

    def test_live_ministry_snapshots(self):
        for cid,name in [(158,'بوجدور'),(1,'الرباط')]:
            html=(ROOT/'tests/fixtures'/f'habous_{cid}_2026_10.html').read_text()
            result=sync.parse_month(html,{'id':cid,'name':name},NOW)
            self.assertEqual(len(result['days']),29)
            self.assertEqual(result['lastDate'],'2026-11-10')
            if cid==158:
                self.assertEqual(result['days'][3]['times'],dict(zip(sync.KEYS,['05:36','06:55','12:49','16:02','18:33','19:42'])))

    def test_bad_sources_rejected(self):
        html = fixture()
        invalid = [html.replace('value="158"', 'value="1"'), html.replace('2026', ''),
                   html.replace('19:43', '29:43'), html.replace('18:34', '12:00'),
                   html.replace('05:35', 'broken'), fixture(count=28),
                   html.replace('<td>15</td>', '<td>16</td>')]
        for text in invalid:
            with self.assertRaises(ValueError):
                sync.parse_month(text, CITY, NOW)
        with self.assertRaises(ValueError):
            sync.parse_month(html, CITY, dt.datetime(2027, 10, 16, tzinfo=dt.timezone.utc))

    def test_year_rollover(self):
        result = sync.parse_month(fixture(start='2026-12-20', heading='دجنبر 2026 / يناير 2027', count=29), CITY,
                                  dt.datetime(2026, 12, 25, tzinfo=dt.timezone.utc))
        self.assertEqual(result['lastDate'], '2027-01-17')

    def test_all_catalog_ids_isolated(self):
        cities = json.loads((ROOT / 'cities.json').read_text())
        self.assertEqual(len(cities), 191)
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            result = sync.synchronize(cities, out, lambda c: sync.parse_month(fixture(c), c, NOW))
            self.assertTrue(all(x['success'] for x in result))
            for city in cities:
                saved = json.loads((out / f'{city["id"]}.json').read_text())
                self.assertEqual(saved['cityId'], city['id'])
                self.assertTrue(all(d['cityId'] == city['id'] for d in saved['days']))

    def test_failure_preserves_last_file(self):
        cities = [{'id': 1, 'name': 'الرباط'}, {'id': 158, 'name': 'بوجدور'}, {'id': 165, 'name': 'الداخلة'}]
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            old = b'{"preserved":true}'
            (out / '1.json').write_bytes(old)
            def offline(city):
                raise OSError('network unavailable')
            result = sync.synchronize(cities, out, offline)
            self.assertEqual(len(result), 2)
            self.assertEqual((out / '1.json').read_bytes(), old)
            self.assertFalse((out / '158.json').exists())

    def test_no_untrusted_redirect(self):
        with self.assertRaises(ValueError):
            sync.MinistryRedirect().redirect_request(None, None, 302, '', {}, 'http://www.habous.gov.ma/prieres/')
        with self.assertRaises(ValueError):
            sync.MinistryRedirect().redirect_request(None, None, 302, '', {}, 'https://other.invalid/')


if __name__ == '__main__':
    unittest.main()
