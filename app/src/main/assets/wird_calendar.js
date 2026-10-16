/* Moroccan calendar: official daily reference when available, calibrated civil fallback. */
(()=>{'use strict';
const DAY=86400000,MONTHS=['محرم','صفر','ربيع الأول','ربيع الآخر','جمادى الأولى','جمادى الآخرة','رجب','شعبان','رمضان','شوال','ذو القعدة','ذو الحجة'];
const GM=['يناير','فبراير','مارس','أبريل','ماي','يونيو','يوليوز','غشت','شتنبر','أكتوبر','نونبر','دجنبر'];
const WD=['الأحد','الاثنين','الثلاثاء','الأربعاء','الخميس','الجمعة','السبت'];
const KEY='wird_official_hijri_v7567';
const store=()=>{try{return window.localStorage}catch{return null}};
const read=k=>{try{return store()?.getItem(k)||''}catch{return''}};
function civil(ordinal){let days=ordinal-Math.floor(Date.UTC(622,6,19)/DAY),year=Math.floor((30*days+10646)/10631);const start=y=>354*(y-1)+Math.floor((3+11*y)/30);while(days<start(year))year--;while(days>=start(year+1))year++;let left=days-start(year),month=1;while(month<12){const length=month%2?30:29;if(left<length)break;left-=length;month++}return{year,month,day:left+1}}
function parts(now=new Date()){
 const country=read('quran_pr_country')||'المغرب';const morocco=country==='المغرب';const zone=morocco?'UTC':read('quran_pr_timezone')||Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC';
 let p;try{p=new Intl.DateTimeFormat('en-CA',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(now)}catch{p=new Intl.DateTimeFormat('en-CA',{timeZone:'UTC',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(now)}
 const n=k=>Number(p.find(x=>x.type===k).value),y=n('year'),m=n('month'),d=n('day'),ordinal=Math.floor(Date.UTC(y,m-1,d)/DAY);
 return{year:y,month:m,day:d,ordinal,dateKey:`${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`,weekday:WD[new Date(ordinal*DAY).getUTCDay()],morocco};
}
function normalizeMonth(name){const s=String(name||'').replace(/ربيع الثاني/g,'ربيع الآخر').replace(/جمادى الثانية/g,'جمادى الآخرة').replace(/[أإآ]/g,'ا').replace(/ى/g,'ي').replace(/[\sـ]+/g,'');return MONTHS.findIndex(x=>x.replace(/[أإآ]/g,'ا').replace(/ى/g,'ي').replace(/[\sـ]+/g,'')===s)+1}
function reference(){try{return JSON.parse(read(KEY)||'null')}catch{return null}}
function monthLength(year,month,ref){
 if(year===1448&&month===4)return 30; // Habous reference month has 30 days, unlike the tabular month.
 if(ref&&year===ref.year&&month===ref.month&&ref.day===30)return 30;
 return month===12?(((11*year+14)%30)<11?30:29):month%2?30:29;
}
function observed(ordinal,ref){
 const [gy,gm,gd]=ref.dateKey.split('-').map(Number);let left=ordinal-(Math.floor(Date.UTC(gy,gm-1,gd)/DAY)-(ref.day-1)),year=ref.year,month=ref.month;
 while(left<0){month--;if(month===0){month=12;year--}left+=monthLength(year,month,ref)}
 while(left>=monthLength(year,month,ref)){left-=monthLength(year,month,ref);month++;if(month===13){month=1;year++}}
 return{year,month,day:left+1};
}
function ingestOfficial(o){
 if(!o||!o.success||o.source!=='habous.gov.ma'||!o.hijri||!/^\d{4}-\d{2}-\d{2}$/.test(o.dateKey||''))return false;
 const h=o.hijri,month=Number(h.monthNumber)||normalizeMonth(h.month),year=Number(h.year),day=Number(h.day);if(month<1||month>12||year<1300||year>1600||day<1||day>30)return false;
 try{store()?.setItem(KEY,JSON.stringify({dateKey:o.dateKey,year,month,day}))}catch{}
 refresh();return true;
}
function snapshot(now=new Date(),adjust){
 const g=parts(now),ref=reference();const manual=adjust===undefined?(parseInt(read('q_hijri_adjust')||'0',10)||0):Number(adjust)||0;
 // Observed Moroccan month: 12 Oct 2026 is 30 Rabi II. Do not force it into a 29-day civil month.
 const base=ref&&ref.year>=1300&&ref.month>=1&&ref.month<=12&&ref.day>=1&&ref.day<=30?ref:{dateKey:'2026-10-12',year:1448,month:4,day:30};
 const h=g.morocco?observed(g.ordinal+manual,base):civil(g.ordinal+manual);let source='حساب محلي';
 if(g.morocco){source=ref&&ref.dateKey===g.dateKey?'وزارة الأوقاف المغربية':g.dateKey==='2026-10-12'?'مطابق لمرجع الأوقاف':'حساب محلي مضبوط على مرجع الأوقاف'}
 if(manual)source+=' • تصحيح يدوي '+(manual>0?'+':'')+manual+' يوم';
 return{...g,hijri:h,adjust:manual,source,gregorianText:`${g.day} ${GM[g.month-1]} ${g.year} م`,hijriText:`${g.weekday}، ${h.day} ${MONTHS[h.month-1]} ${h.year} هـ`,hijriDateText:`${h.day} ${MONTHS[h.month-1]} ${h.year} هـ`};
}
window.wirdCalendarV7567={snapshot,ingestOfficial,civil,observed,normalizeMonth};
window.wirdHomeDateCardV7567=function(){const x=snapshot();return `<section class="homeDateV7567" aria-label="تاريخ اليوم"><div class="homeDateDayV7567" id="home-date-day-v7567">${x.weekday}</div><div class="homeDateValuesV7567"><div><small>الميلادي</small><strong id="home-date-g-v7567">${x.gregorianText}</strong></div><div><small>الهجري</small><strong id="home-date-h-v7567">${x.hijriDateText}</strong></div></div><div class="homeDateSourceV7567" id="home-date-source-v7567">${x.source}</div></section>`};
function refresh(){const x=snapshot();for(const [id,value] of [['home-date-day-v7567',x.weekday],['home-date-g-v7567',x.gregorianText],['home-date-h-v7567',x.hijriDateText],['home-date-source-v7567',x.source]]){const el=document.getElementById(id);if(el&&el.textContent!==value)el.textContent=value}}
window.refreshHomeDateV7567=refresh;
setInterval(refresh,60000);document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh()});window.addEventListener('pageshow',refresh);
})();
