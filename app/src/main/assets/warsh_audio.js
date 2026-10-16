/* ═══════════════════════════════════════════════════════
   وِرد — التلاوة الصوتية بورش V1.2
   المصدر: mp3quran.net API v3
   • تشغيل على مستوى السورة + تتابع تلقائي للسور
   • قائمة القراء تُجلب مرة وتُخزَّن 7 أيام
   • عنصر Audio واحد يُعاد استخدامه
   • شريط حالة مستقل #waAudioStatus
   V1.2 adds offline surah downloads per reciter.
   V1.1 fixes:
   - support official API field surah_list
   - serialize cached surah lists as arrays, then rebuild Set
════════════════════════════════════════════════════════ */
(function(){
'use strict';

const WA_API='https://mp3quran.net/api/v3/reciters?language=ar';
const K_LIST='q_warsh_audio_list', K_REC='q_warsh_audio_reciter';
const CACHE_MS=7*864e5;
const WA_DB='wird_warsh_audio_v1', WA_STORE='tracks';
let waDbPromise=null, activeBlobUrl=null;

function openWaDb(){
  if(!('indexedDB' in window))return Promise.reject(new Error('IndexedDB غير متاح'));
  if(waDbPromise)return waDbPromise;
  waDbPromise=new Promise((resolve,reject)=>{
    const q=indexedDB.open(WA_DB,1);
    q.onupgradeneeded=()=>{
      const db=q.result;
      if(!db.objectStoreNames.contains(WA_STORE)){
        const st=db.createObjectStore(WA_STORE,{keyPath:'key'});
        st.createIndex('reciter','reciterId',{unique:false});
      }
    };
    q.onsuccess=()=>resolve(q.result);
    q.onerror=()=>reject(q.error||new Error('تعذر فتح التخزين'));
  });
  return waDbPromise;
}
function trackKey(recId,sura){return String(recId)+'|'+String(Number(sura)).padStart(3,'0')}
async function waDbGet(recId,sura){
  const db=await openWaDb();
  return new Promise((resolve,reject)=>{
    const tx=db.transaction(WA_STORE,'readonly');
    const q=tx.objectStore(WA_STORE).get(trackKey(recId,sura));
    q.onsuccess=()=>resolve(q.result||null);
    q.onerror=()=>reject(q.error);
  });
}
async function waDbPut(row){
  const db=await openWaDb();
  return new Promise((resolve,reject)=>{
    const tx=db.transaction(WA_STORE,'readwrite');
    tx.objectStore(WA_STORE).put(row);
    tx.oncomplete=()=>resolve(true);
    tx.onerror=()=>reject(tx.error);
  });
}
async function waDbDelete(recId,sura){
  const db=await openWaDb();
  return new Promise((resolve,reject)=>{
    const tx=db.transaction(WA_STORE,'readwrite');
    tx.objectStore(WA_STORE).delete(trackKey(recId,sura));
    tx.oncomplete=()=>resolve(true);
    tx.onerror=()=>reject(tx.error);
  });
}
async function waDbList(recId){
  const db=await openWaDb();
  return new Promise((resolve,reject)=>{
    const out=[];
    const tx=db.transaction(WA_STORE,'readonly');
    const ix=tx.objectStore(WA_STORE).index('reciter');
    const q=ix.openCursor(IDBKeyRange.only(String(recId)));
    q.onsuccess=()=>{
      const c=q.result;
      if(c){out.push(c.value);c.continue()}
      else resolve(out);
    };
    q.onerror=()=>reject(q.error);
  });
}

/* ── CSS صغير للشريط المستقل ── */
const st=document.createElement('style');
st.textContent=
'#waAudioStatus .waPick{display:flex;align-items:center;background:none;border:0;color:#fff;font-size:17px;cursor:pointer;border-radius:14px;padding:4px 8px}'+
'#waAudioStatus .waPick:active{background:#ffffff18}'+
'#waAudioStatus .waBar{height:3px;background:#ffffff22;border-radius:99px;margin-top:6px;overflow:hidden}'+
'#waAudioStatus .waBar i{display:block;height:100%;width:0;background:var(--accent,#c9a34b)}'+
'#waAudioStatus.waMini{width:auto;min-width:0;left:14px;right:auto;transform:none;padding:6px;border-radius:999px;bottom:82px}'+
'#waAudioStatus.waMini .waPick{width:42px;height:42px;justify-content:center;padding:0;font-size:20px}';
document.head.appendChild(st);

/* ── الحالة ── */
let list=null, audio=null;
let minimized=false;
let cur={rec:null,sura:0,playing:false};

const esc=s=>String(s??'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const T=m=>{if(typeof toast==='function')toast(m);else console.log('[WirdAudio]',m)};
const save=(k,v)=>{try{localStorage.setItem(k,JSON.stringify(v))}catch(e){console.warn('[WirdAudio cache]',e)}};
const load=k=>{try{const x=localStorage.getItem(k);return x?JSON.parse(x):null}catch(e){return null}};

function hydrateRec(r){
  if(!r)return null;
  const arr=Array.isArray(r.surahs)?r.surahs:
    Array.isArray(r.surSet)?r.surSet:
    (r.surSet&&typeof r.surSet[Symbol.iterator]==='function'?[...r.surSet]:[]);
  return {...r,surahs:arr.map(Number).filter(Boolean),surSet:new Set(arr.map(Number).filter(Boolean))};
}
function serialRec(r){
  return {
    id:r.id,name:r.name,riwaya:r.riwaya,server:r.server,
    surahs:[...(r.surSet||new Set(r.surahs||[]))]
  };
}

/* ── جلب قرّاء ورش (مع كاش أسبوعي) ── */
async function warshReciters(force){
  if(!force&&list)return list;

  const c=load(K_LIST);
  if(!force&&c&&Array.isArray(c.list)&&c.list.length&&Date.now()-Number(c._t||0)<CACHE_MS){
    list=c.list.map(hydrateRec).filter(Boolean);
    if(list.length)return list;
  }

  const r=await fetch(WA_API,{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);

  const j=await r.json(), out=[];
  (j.reciters||[]).forEach(rc=>(rc.moshaf||[]).forEach(m=>{
    if(!/ورش/.test(String(m.name||'')))return;
    const rawSurahs=String(m.surah_list||m.sur_list||'');
    const surahs=rawSurahs.split(',').map(Number).filter(n=>n>=1&&n<=114);
    out.push(hydrateRec({
      id:String(rc.id)+'_'+String(m.id),
      name:String(rc.name||'قارئ'),
      riwaya:String(m.name||'ورش عن نافع'),
      server:String(m.server||'').replace(/\/?$/,'/'),
      surahs
    }));
  }));

  if(!out.length)throw new Error('لا روايات ورش في الرد');
  out.sort((a,b)=>a.name.localeCompare(b.name,'ar'));
  list=out;
  save(K_LIST,{_t:Date.now(),list:list.map(serialRec)});
  return list;
}

/* ── شريط الحالة ── */
function bar(){
  let b=document.getElementById('waAudioStatus');
  if(!b){
    b=document.createElement('div');
    b.id='waAudioStatus';
    b.className='audioStatus';
    document.body.appendChild(b);
  }
  return b;
}
function paintBar(){
  const b=bar();

  /* الإغلاق الحقيقي: إذا لا توجد سورة نشطة لا يظهر الشريط */
  if(!cur.rec||!cur.sura){
    b.classList.remove('show','waMini');
    b.innerHTML='';
    return;
  }

  /* التصغير: تبقى التلاوة شغالة ويظهر زر سماعة صغير فقط */
  if(minimized){
    b.classList.add('show','waMini');
    b.innerHTML='<button class="waPick" onclick="waRestore()" title="إظهار مشغّل ورش">🎧</button>';
    return;
  }

  b.classList.remove('waMini');
  const sName=(typeof SURAHS!=='undefined'&&SURAHS[cur.sura-1])?SURAHS[cur.sura-1].name:'';
  b.innerHTML=
   '<button class="waPick" onclick="warshAudioPick()" title="تغيير القارئ">🎧</button>'
  +'<div class="grow"><strong>'+esc(sName)+'</strong><small>'+esc(cur.rec.name)+' — '+esc(cur.rec.riwaya||'ورش')+'</small>'
  +'<div class="waBar"><i id="waProg"></i></div></div>'
  +'<button onclick="waMinimize()" title="تصغير المشغّل">⌄</button>'
  +'<button onclick="waToggle()">'+(cur.playing?'⏸':'▶')+'</button>'
  +'<button onclick="warshAudioStop()" title="إغلاق المشغّل">✕</button>';
  b.classList.add('show');
}

function waMinimize(){minimized=true;paintBar()}
function waRestore(){minimized=false;paintBar()}

/* ── المشغّل ── */
function ensureAudio(){
  if(audio)return audio;
  audio=new Audio();
  audio.preload='metadata';
  audio.addEventListener('ended',onEnded);
  audio.addEventListener('timeupdate',()=>{
    const p=document.getElementById('waProg');
    if(p&&Number.isFinite(audio.duration)&&audio.duration>0)
      p.style.width=(audio.currentTime/audio.duration*100)+'%';
  });
  audio.addEventListener('play',()=>{cur.playing=true;paintBar()});
  audio.addEventListener('pause',()=>{cur.playing=false;paintBar()});
  audio.addEventListener('error',()=>{cur.playing=false;T('تعذّر تحميل تلاوة ورش — تحقق من الاتصال');paintBar()});
  return audio;
}

async function warshAudioPlay(sura,rec){
  sura=Number(sura);
  if(!Number.isInteger(sura)||sura<1||sura>114)return;

  if(rec)cur.rec=hydrateRec(rec);
  if(!cur.rec){warshAudioPick(sura);return}

  if(cur.rec.surSet.size&&!cur.rec.surSet.has(sura)){
    T('هذا القارئ لا يملك هذه السورة');
    return;
  }

  cur.sura=sura;
  minimized=false;
  const a=ensureAudio();

  if(activeBlobUrl){
    try{URL.revokeObjectURL(activeBlobUrl)}catch(e){}
    activeBlobUrl=null;
  }

  try{
    const local=await waDbGet(cur.rec.id,sura);
    if(local&&local.blob){
      activeBlobUrl=URL.createObjectURL(local.blob);
      a.src=activeBlobUrl;
      a.dataset.waOffline='1';
      T('▶ تشغيل السورة المحمّلة بدون إنترنت');
    }else{
      a.src=cur.rec.server+String(sura).padStart(3,'0')+'.mp3';
      a.dataset.waOffline='0';
    }
  }catch(e){
    a.src=cur.rec.server+String(sura).padStart(3,'0')+'.mp3';
    a.dataset.waOffline='0';
  }

  try{
    if('mediaSession' in navigator&&typeof MediaMetadata!=='undefined'&&typeof SURAHS!=='undefined'&&SURAHS[sura-1]){
      navigator.mediaSession.metadata=new MediaMetadata({
        title:'سورة '+SURAHS[sura-1].name,
        artist:cur.rec.name+' — ورش',
        album:'وِرد'
      });
    }
  }catch(e){console.warn('[WirdAudio MediaSession]',e)}

  a.play().catch(()=>{paintBar();T('اضغط ▶ في شريط تلاوة ورش للتشغيل')});
  paintBar();
}

function waToggle(){
  const a=ensureAudio();
  if(!a.src)return;
  a.paused?a.play().catch(()=>T('تعذّر تشغيل الصوت عبر الشبكة')):a.pause();
}
function warshAudioStop(){
  if(audio){
    audio.pause();
    audio.removeAttribute('src');
    try{audio.load()}catch(e){}
  }
  if(activeBlobUrl){
    try{URL.revokeObjectURL(activeBlobUrl)}catch(e){}
    activeBlobUrl=null;
  }
  cur.sura=0;cur.playing=false;minimized=false;paintBar();
}
function onEnded(){
  if(!cur.rec){warshAudioStop();return}
  let n=cur.sura+1;
  while(n<=114&&cur.rec.surSet.size&&!cur.rec.surSet.has(n))n++;
  if(n<=114&&(cur.rec.surSet.size===0||cur.rec.surSet.has(n)))warshAudioPlay(n);
  else{T('انتهت التلاوة 🎧');warshAudioStop()}
}

/* ── شريحة اختيار القارئ ── */
function waCloseSheet(){
  const h=document.getElementById('sheetHost');
  if(h)h.innerHTML='';
  else{
    const w=document.getElementById('waWrap');
    if(w)w.remove();
  }
}
function waSheet(title,html){
  const appHost=document.getElementById('sheetHost');
  const host=appHost||document.body;
  host.innerHTML='<div class="sheetWrap" id="waWrap"><div class="sheet"><div class="handle"></div><h3>'+title+'</h3>'+html+'</div></div>';
  const w=document.getElementById('waWrap');
  if(w)w.addEventListener('click',e=>{if(e.target.id==='waWrap')waCloseSheet()});
}


function waBytes(n){
  n=Number(n)||0;
  if(n<1024)return n+' B';
  if(n<1048576)return (n/1024).toFixed(1)+' KB';
  return (n/1048576).toFixed(1)+' MB';
}
async function warshAudioDownload(sura,btn){
  sura=Number(sura);
  if(!cur.rec){T('اختر قارئ ورش أولاً');warshAudioPick();return}
  if(cur.rec.surSet.size&&!cur.rec.surSet.has(sura)){T('هذه السورة غير متوفرة لهذا القارئ');return}
  const old=btn?btn.textContent:'';
  if(btn){btn.disabled=true;btn.textContent='جارٍ…'}
  try{
    const url=cur.rec.server+String(sura).padStart(3,'0')+'.mp3';
    const r=await fetch(url,{cache:'no-store'});
    if(!r.ok)throw new Error('HTTP '+r.status);
    const blob=await r.blob();
    if(!blob.size)throw new Error('ملف فارغ');
    await waDbPut({
      key:trackKey(cur.rec.id,sura),
      reciterId:String(cur.rec.id),
      reciterName:cur.rec.name,
      riwaya:cur.rec.riwaya,
      sura,
      blob,
      size:blob.size,
      savedAt:Date.now()
    });
    T('✓ تم تحميل السورة — '+waBytes(blob.size));
    warshAudioDownloads(sura);
  }catch(e){
    console.warn('[WirdAudio download]',e);
    T('تعذّر تحميل السورة — تحقق من الإنترنت والمساحة');
    if(btn){btn.disabled=false;btn.textContent=old||'تحميل'}
  }
}
async function warshAudioDelete(sura){
  if(!cur.rec)return;
  try{
    await waDbDelete(cur.rec.id,Number(sura));
    T('تم حذف السورة المحمّلة');
    warshAudioDownloads(Number(sura));
  }catch(e){T('تعذّر حذف السورة')}
}
async function warshAudioDownloads(focusSura){
  if(!cur.rec){warshAudioPick();return}
  const downloaded=await waDbList(cur.rec.id).catch(()=>[]);
  const bySura=new Map(downloaded.map(x=>[Number(x.sura),x]));
  const rows=[];
  for(let s=1;s<=114;s++){
    if(cur.rec.surSet.size&&!cur.rec.surSet.has(s))continue;
    const nm=(typeof SURAHS!=='undefined'&&SURAHS[s-1])?SURAHS[s-1].name:('السورة '+s);
    const d=bySura.get(s);
    rows.push(
      '<div class="row" id="waSur'+s+'" style="align-items:center">'
      +'<div class="num">'+s+'</div>'
      +'<div class="rowmain"><strong>'+esc(nm)+'</strong>'
      +'<div class="meta">'+(d?'✓ محمّلة • '+waBytes(d.size):'غير محمّلة')+'</div></div>'
      +(d
        ?'<button class="btn" onclick="warshAudioPlay('+s+')" style="margin-inline:4px">▶</button><button class="btn" onclick="warshAudioDelete('+s+')">حذف</button>'
        :'<button class="btn" onclick="warshAudioDownload('+s+',this)">تحميل</button>')
      +'</div>'
    );
  }
  waSheet('⬇ تنزيلات '+esc(cur.rec.name),
    '<div class="meta" style="margin-bottom:10px">اختر أي سورة لهذا القارئ. بعد التحميل سيستعمل وِرد النسخة المحلية تلقائياً بدون إنترنت.</div>'
    +'<div style="max-height:62vh;overflow:auto" id="waDownloadList">'+rows.join('')+'</div>');
  if(focusSura){
    setTimeout(()=>{const el=document.getElementById('waSur'+Number(focusSura));if(el)el.scrollIntoView({block:'center'})},50);
  }
}

async function warshAudioPick(playAfter){
  try{
    waSheet('🎧 قرّاء ورش عن نافع','<div style="text-align:center;color:var(--muted);padding:30px">جارٍ التحميل…</div>');
    const ls=await warshReciters();
    const chosen=String(load(K_REC)||'');

    waSheet('🎧 قرّاء ورش عن نافع',ls.map((r,i)=>
      '<div class="row" data-i="'+i+'" style="cursor:pointer">'
      +'<div class="num">'+(chosen===r.id?'✓':(i+1))+'</div>'
      +'<div class="rowmain"><strong>'+esc(r.name)+'</strong>'
      +'<div class="meta">'+esc(r.riwaya)+(r.surSet.size&&r.surSet.size<114?' • '+r.surSet.size+' سورة':'')+'</div></div>'
      +'<button class="btn waManage" data-mi="'+i+'" type="button">تنزيلات</button></div>'
    ).join(''));

    document.querySelectorAll('#waWrap .row').forEach(el=>el.onclick=e=>{
      if(e.target&&e.target.classList&&e.target.classList.contains('waManage'))return;
      const r=ls[Number(el.dataset.i)];
      if(!r)return;
      cur.rec=r;
      save(K_REC,r.id);
      waCloseSheet();
      T('🎧 '+r.name);
      if(playAfter)warshAudioPlay(Number(playAfter));
    });
    document.querySelectorAll('#waWrap .waManage').forEach(btn=>btn.onclick=e=>{
      e.stopPropagation();
      const r=ls[Number(btn.dataset.mi)];
      if(!r)return;
      cur.rec=r;
      save(K_REC,r.id);
      warshAudioDownloads();
    });
  }catch(e){
    console.warn('[WirdAudio]',e);
    waSheet('🎧 قرّاء ورش',
      '<div class="empty">تعذّر جلب القائمة.<br><small>أول استخدام يحتاج إنترنت، وبعدها تُخزَّن القائمة 7 أيام.</small></div>'
      +'<button class="btn" style="width:100%" onclick="waCloseSheet()">حسناً</button>');
  }
}

/* ── استرجاع القارئ المحفوظ عند الإقلاع ── */
(async function(){
  try{
    const id=String(load(K_REC)||'');
    if(!id)return;
    const r=(await warshReciters()).find(x=>x.id===id);
    if(r)cur.rec=r;
  }catch(e){console.warn('[WirdAudio restore]',e)}
})();

/* ── ربطات اختيارية ── */
function waHooks(){
  /* زر التشغيل الرئيسي: ورش → سورة كاملة. حفص يبقى كما هو. */
  if(typeof window.playCurrent==='function'&&!window.playCurrent.__waHooked){
    const orig=window.playCurrent;
    window.playCurrent=function(){
      if(typeof state!=='undefined'&&state.riwaya==='warsh'){
        warshAudioPlay(Number(state.sura||1));
        return;
      }
      return orig.apply(this,arguments);
    };
    window.playCurrent.__waHooked=true;
  }

  /* تغيير الرواية إلى حفص يوقف تلاوة ورش. */
  if(typeof window.chooseRiwaya==='function'&&!window.chooseRiwaya.__waHooked){
    const oc=window.chooseRiwaya;
    window.chooseRiwaya=function(m,s){
      if(m!=='warsh'&&cur.sura)warshAudioStop();
      return oc.apply(this,arguments);
    };
    window.chooseRiwaya.__waHooked=true;
  }
}

/* ── التصدير العالمي ── */
window.warshReciters=warshReciters;
window.warshAudioPick=warshAudioPick;
window.warshAudioPlay=warshAudioPlay;
window.warshAudioStop=warshAudioStop;
window.warshAudioDownload=warshAudioDownload;
window.warshAudioDelete=warshAudioDelete;
window.warshAudioDownloads=warshAudioDownloads;
window.waToggle=waToggle;
window.waMinimize=waMinimize;
window.waRestore=waRestore;
window.waCloseSheet=waCloseSheet;
window.waHooks=waHooks;
window.warshAudioState=()=>({rec:cur.rec?serialRec(cur.rec):null,sura:cur.sura,playing:cur.playing});
})();
