(()=>{
'use strict';
let loadedStamp='',lastPoll=0;
function refresh(){
 const x=window.__WIRD75X,n=window.WirdNative;if(!x||x.country!=='المغرب'||x.method!=='21'||!n?.prayerMonthStatus)return;
 const id=Number(localStorage.getItem('quran_pr_city_id')||0);let s;
 try{s=JSON.parse(n.prayerMonthStatus(id))}catch(_){return}
 const parent=document.querySelector('.prayerPage');if(parent){
  let box=document.getElementById('prayerMonthStatusV7580');if(!box){box=document.createElement('div');box.id='prayerMonthStatusV7580';box.className='prayerDayInfoV7577';box.dir='rtl';box.setAttribute('role','status');parent.querySelector('.prayerHero')?.after(box)}
  let text='الجداول الشهرية المحفوظة لليوم: '+(s.ready||0)+' / '+(s.total||191)+' مدينة.';
  if(x.imported)text+=' • المعروض جدول مستورد من اختيارك.';
  else if(s.todayAvailable)text+=' • حصة '+x.city+' محفوظة حتى '+s.coverageEnd+'.';
  else text+=' • حصة '+x.city+' الرسمية لم تُحمّل بعد؛ المعروض حساب احتياطي إن توفرت الإحداثيات.';
  if(s.error)text+=' • تعذر الاتصال بالمصدر؛ سيُعاد التحديث تلقائياً.';
  text+=' • تتجدد الحصص المنشورة تلقائياً مع الإنترنت.';
  if(box.textContent!==text)box.textContent=text;
 }
 const stamp=id+'|'+s.fetchedAt;
 if(s.todayAvailable&&loadedStamp!==stamp){loadedStamp=stamp;window.loadPrayerByCityV75X?.(false)}
}
function poll(){if(document.hidden||Date.now()-lastPoll<60000)return;lastPoll=Date.now();refresh()}
function init(){new MutationObserver(poll).observe(document.getElementById('content'),{childList:true});setInterval(poll,60000);refresh()}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
document.addEventListener('visibilitychange',()=>{if(!document.hidden){lastPoll=0;poll()}});
window.refreshAllPrayerMonthsV7580=()=>{window.WirdNative?.refreshPrayerMonths?.();window.loadPrayerByCityV75X?.(true);lastPoll=0;poll()};
window.refreshPrayerMonthStatusV7580=()=>{lastPoll=0;poll()};
})();
