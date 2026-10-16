(()=>{
'use strict';
let fingerprint='';
const signature=()=>[new Intl.DateTimeFormat('en-CA',{timeZone:localStorage.getItem('quran_pr_timezone')||'UTC',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()),new Date().getTimezoneOffset(),localStorage.getItem('quran_pr_city'),localStorage.getItem('quran_pr_method')].join('|');
function refresh(){
 const current=signature();if(current===fingerprint)return;fingerprint=current;
 if(typeof window.loadPrayerByCityV75X==='function')window.loadPrayerByCityV75X(false);
 window.refreshNativeStatusV71?.();
}
function details(){
 const page=document.querySelector('.prayerPage'),x=window.__WIRD75X;if(!page||!x)return;
 let box=document.getElementById('prayerDayInfoV7577');
 if(!box){box=document.createElement('div');box.id='prayerDayInfoV7577';box.className='prayerDayInfoV7577';page.querySelector('.prayerHero')?.after(box)}
 const today=x.days?.[0],tomorrow=x.days?.[1];
 const source=d=>d?.source==='official'?'جدول الوزارة':d?.source==='imported'?'جدول مستورد':'حساب محلي احتياطي';
 box.textContent=x.unavailable?'الجدول الرسمي غير متوفر لهذه المدينة بعد':today?'اليوم '+today.dateKey+' — '+source(today)+(tomorrow?' • فجر الغد '+tomorrow.times.Fajr+' — '+source(tomorrow):''):'مواقيت اليوم — '+(x.official?'جدول الوزارة':'حساب محلي');
 if(!page.querySelector('#prayerClockFormatV7577')){
  const label=document.createElement('label');label.className='prayerDayInfoV7577';label.textContent='عرض الساعة: ';
  const select=document.createElement('select');select.id='prayerClockFormatV7577';select.setAttribute('aria-label','صيغة عرض الساعة');select.innerHTML='<option value="24">24 ساعة</option><option value="12">12 ساعة</option>';select.value=localStorage.getItem('wird_prayer_clock_format')||'24';select.onchange=()=>{localStorage.setItem('wird_prayer_clock_format',select.value);formatTimes()};label.append(select);box.after(label);
 }
 formatTimes();
}
function formatTimes(){
 const twelve=localStorage.getItem('wird_prayer_clock_format')==='12';
 document.querySelectorAll('.prayerTimeCardV72 .time,.home50PrayerTime b').forEach(el=>{
  const raw=el.dataset.rawTime||el.textContent.trim();if(!/^\d{2}:\d{2}$/.test(raw))return;el.dataset.rawTime=raw;
  const [h,m]=raw.split(':').map(Number);const shown=twelve?`${h%12||12}:${String(m).padStart(2,'0')} ${h<12?'ص':'م'}`:raw;
  if(el.textContent!==shown)el.textContent=shown;
 });
}
window.refreshPrayerDayV7577=()=>{fingerprint='';refresh();details()};
function init(){fingerprint=signature();details();new MutationObserver(()=>details()).observe(document.getElementById('content'),{childList:true,subtree:false});setInterval(()=>{refresh();details()},60000)}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
document.addEventListener('visibilitychange',()=>{if(!document.hidden){refresh();details()}});
})();
