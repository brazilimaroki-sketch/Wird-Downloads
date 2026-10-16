(()=>{
'use strict';
const EDITIONS=[{id:'eng-ummmuhammad',name:'English — Umm Muhammad',lang:'en'},{id:'fra-muhammadhamidul',name:'Français — Muhammad Hamidullah',lang:'fr'}];
let dbPromise,downloadToken=0,translationToken=0;
function db(){return dbPromise||(dbPromise=new Promise((resolve,reject)=>{const r=indexedDB.open('wird_reader_media_v7577',1);r.onupgradeneeded=()=>r.result.createObjectStore('items',{keyPath:'id'});r.onsuccess=()=>resolve(r.result);r.onerror=()=>{dbPromise=null;reject(r.error)}}))}
async function get(id){const d=await db();return new Promise((resolve,reject)=>{const r=d.transaction('items').objectStore('items').get(id);r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)})}
async function put(value){const d=await db();return new Promise((resolve,reject)=>{const t=d.transaction('items','readwrite');t.objectStore('items').put(value);t.oncomplete=resolve;t.onerror=()=>reject(t.error);t.onabort=()=>reject(t.error)})}
const mediaKey=(r,s,a)=>`audio:${r}:${s}:${a}`;
window.hafsCachedAudioV7577=async function(reciter,s,a){try{return (await get(mediaKey(reciter,s,a)))?.blob||null}catch{return null}};
async function fetchBounded(url){const ctrl=new AbortController(),timer=setTimeout(()=>ctrl.abort(),20000);try{const r=await fetch(url,{signal:ctrl.signal});if(!r.ok)throw Error('HTTP '+r.status);return await r.blob()}finally{clearTimeout(timer)}}
window.downloadHafsSurahV7577=async function(s){
 if(state.riwaya==='warsh'){toast('تحميل ورش متاح من قائمة قرّاء ورش');return}
 const rec=reciterInfo();if(!rec||rec.audiotype!==0){toast('اختر قارئاً بنظام آية بآية');return}
 const rows=(bySura.get(Number(s))||[]).filter(x=>x[1]>0),token=++downloadToken,id=rec.id;
 sheetHost.innerHTML='<div class="sheetWrap"><div class="sheet"><h3>تحميل سورة '+esc(SURAHS[s-1].name)+'</h3><p id="hafsDownloadStatusV7577" role="status">جاري التجهيز…</p><button class="btn" onclick="cancelHafsDownloadV7577()">إيقاف التحميل</button><button class="btn soft" onclick="deleteHafsSurahV7577('+s+')">حذف التلاوة المحفوظة</button><p class="meta">يُحفظ كل ملف مكتمل. يمكن استكمال التحميل لاحقاً، وتُستعمل النسخة المحفوظة تلقائياً عند الاستماع.</p></div></div>';
 const setStatus=text=>{const el=document.getElementById('hafsDownloadStatusV7577');if(el)el.textContent=text};
 try{
  for(let i=0;i<rows.length;i++){
   if(token!==downloadToken)return;
   const a=rows[i][1],key=mediaKey(id,s,a);let item=await get(key);
   if(!item){const base=String(rec.url||'').replace(/^http:/,'https:');if(!base.startsWith('https:'))throw Error('Invalid audio source');
    const blob=await fetchBounded(base+String(s).padStart(3,'0')+String(a).padStart(3,'0')+'.mp3');
    if(blob.size<256||blob.size>8*1024*1024)throw Error('Invalid audio size');const b=new Uint8Array(await blob.slice(0,3).arrayBuffer());
    if(!(b[0]===73&&b[1]===68&&b[2]===51)&&!(b[0]===255&&(b[1]&224)===224))throw Error('Invalid MP3');
    if(token!==downloadToken)return;await put({id:key,blob,size:blob.size});
   }
   setStatus(`تم حفظ ${i+1} / ${rows.length} آية — ${rec.name}`);
  }
  if(token===downloadToken){setStatus('اكتمل التحميل — جاهز للاستماع بلا إنترنت');toast('تم تحميل تلاوة السورة')}
 }catch(e){if(token===downloadToken)setStatus('توقف التحميل؛ تحقق من الإنترنت والمساحة، ثم أعد المحاولة للاستكمال.')}
};
window.deleteHafsSurahV7577=async function(s){
 downloadToken++;const prefix=`audio:${reciterInfo().id}:${s}:`;
 try{const d=await db();const keys=await new Promise((resolve,reject)=>{const r=d.transaction('items').objectStore('items').getAllKeys();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)});
  await new Promise((resolve,reject)=>{const t=d.transaction('items','readwrite'),store=t.objectStore('items');for(const key of keys)if(String(key).startsWith(prefix))store.delete(key);t.oncomplete=resolve;t.onerror=()=>reject(t.error)});
  closeSheet();toast('تم حذف التلاوة المحفوظة لهذا القارئ والسورة')
 }catch(e){toast('تعذر حذف التلاوة المحفوظة')}
};
window.cancelHafsDownloadV7577=()=>{downloadToken++;closeSheet();toast('توقف التحميل؛ الملفات المكتملة محفوظة')};
window.showTranslationV7577=function(s,a){
 if(state.riwaya==='warsh'){toast('الترجمة متاحة حالياً مع ترقيم حفص لتفادي اختلاف ترقيم الآيات');return}
 const selected=localStorage.getItem('wird_translation_v7577')||EDITIONS[0].id;
 modalHost.innerHTML=`<div class="modal"><div class="dialog"><button class="close" onclick="closeModal()">×</button><h3>ترجمة معاني الآية ${arNum(a)}</h3><select id="translationEditionV7577" class="prayerSelect" aria-label="لغة الترجمة">${EDITIONS.map(e=>`<option value="${e.id}">${esc(e.name)}</option>`).join('')}</select><p class="meta">ترجمة للمعاني وفق ترقيم حفص؛ نص المصحف يبقى كما هو. تُحفظ السورة بعد تحميلها للاستعمال بلا إنترنت.</p><p id="translationBodyV7577" role="status"></p></div></div>`;
 const pick=document.getElementById('translationEditionV7577');pick.value=selected;pick.onchange=()=>loadTranslation(s,a,pick.value);loadTranslation(s,a,pick.value);
};
async function loadTranslation(s,a,id){
 const edition=EDITIONS.find(x=>x.id===id);if(!edition)return;localStorage.setItem('wird_translation_v7577',id);const token=++translationToken;
 const body=document.getElementById('translationBodyV7577');body.textContent='جاري فتح الترجمة…';body.dir='ltr';body.lang=edition.lang;
 try{
  const key=`translation:${id}:${s}`;let saved=await get(key);
  if(!saved){let parsed;
   for(const base of ['https://cdn.jsdelivr.net/gh/fawazahmed0/quran-api@1/editions/','https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/']){
    try{const blob=await fetchBounded(`${base}${id}/${s}.json`);if(blob.size>1024*1024)throw Error('Large translation');parsed=JSON.parse(await blob.text());break}catch(e){parsed=null}
   }
   const rows=parsed?.chapter,expected=(bySura.get(Number(s))||[]).filter(x=>x[1]>0).length;
   if(!Array.isArray(rows)||rows.length!==expected||rows.some((r,i)=>Number(r.chapter)!==Number(s)||Number(r.verse)!==i+1||typeof r.text!=='string'||!r.text.trim()))throw Error('Invalid translation data');
   saved={id:key,rows};await put(saved);
  }
  if(token!==translationToken||document.getElementById('translationBodyV7577')!==body)return;
  const row=saved.rows.find(x=>Number(x.verse)===Number(a));if(!row)throw Error('Missing verse');body.textContent=row.text;
 }catch(e){if(token===translationToken&&document.getElementById('translationBodyV7577')===body){body.dir='rtl';body.textContent='تعذر فتح الترجمة. تحقق من الإنترنت والمساحة أو اختر ترجمة أخرى.'}}
}
const oldSheet=window.openAyaSheet;
window.openAyaSheet=function(s,a){oldSheet.apply(this,arguments);const grid=document.querySelector('#sheetHost .ayahV38Grid');if(!grid)return;
 for(const [label,fn] of [['ترجمة المعاني',()=>window.showTranslationV7577(s,a)],['تحميل تلاوة السورة',()=>window.downloadHafsSurahV7577(s)]]){
  const b=document.createElement('button');b.textContent=label;b.onclick=fn;grid.append(b);
 }
};
window.setHafsRepeatV7577=function(value){state.repeatCount=Math.max(1,Math.min(10,Number(value)||1));state.repeatKey='';localStorage.setItem('wird_hafs_repeat',String(state.repeatCount))};
state.repeatCount=Math.max(1,Math.min(10,Number(localStorage.getItem('wird_hafs_repeat')||1)));
})();
