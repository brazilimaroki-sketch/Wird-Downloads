/* ═══════════════════════════════════════════════════════
   وِرد — أداة تدقيق مصحف ورش V1 (مراجعة + تتبع تقدم)
═══════════════════════════════════════════════════════ */
(function(){
'use strict';

const WA_KEY='q_audit_v1';
const WA_SCOPE='q_audit_scope';

const css=`
.waWrap{position:fixed;inset:0;background:#0009;z-index:10060;display:flex;align-items:flex-end;justify-content:center}
.waBox{width:min(680px,100%);max-height:88dvh;background:var(--card);color:var(--ink);border-radius:22px 22px 0 0;display:flex;flex-direction:column}
.waHead{display:flex;align-items:center;gap:10px;padding:14px 16px 10px;border-bottom:1px solid var(--line)}
.waHead h3{margin:0;font-size:18px;flex:1}.waClose{border:0;background:var(--bg);color:var(--ink);width:34px;height:34px;border-radius:50%;font-size:20px;cursor:pointer}
.waBody{overflow:auto;padding:12px 16px calc(18px + env(safe-area-inset-bottom))}.waStats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-bottom:10px}
.waStat{border:1px solid var(--line);border-radius:14px;padding:10px;text-align:center}.waStat b{display:block;font-size:19px}.waStat span{font-size:11px;color:var(--muted)}
.waBar{height:8px;background:var(--line);border-radius:99px;overflow:hidden;margin:4px 0 12px}.waBar i{display:block;height:100%;background:var(--primary);transition:width .3s}
.waWarn{font-size:11px;color:#8a671c;background:#c9a34b14;border:1px solid #c9a34b33;border-radius:12px;padding:9px 11px;line-height:1.7;margin-bottom:12px}
.waGrid{display:grid;grid-template-columns:repeat(auto-fill,minmax(44px,1fr));gap:6px}.waCell{border:1px solid var(--line);background:var(--bg);color:var(--ink);border-radius:10px;padding:8px 0;font:inherit;font-weight:800;font-size:12px;cursor:pointer;text-align:center}
.waCell.off{opacity:.3;cursor:not-allowed}.waCell.prog{background:#c9a34b33;border-color:var(--accent)}.waCell.done{background:var(--primary);color:#fff;border-color:var(--primary)}
.waTools{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:12px}.waSel,.waRev{border:1px solid var(--line);border-radius:10px;padding:9px 10px;font:inherit;font-size:13px;background:var(--card);color:var(--ink)}
.waBtn{border:0;border-radius:12px;padding:10px 14px;font:inherit;font-weight:900;background:var(--primary);color:#fff;cursor:pointer}.waBtn.soft{background:var(--bg);color:var(--ink);border:1px solid var(--line)}
.waRow{border:1px solid var(--line);border-radius:14px;padding:11px;margin-bottom:9px}.waRow.ok{border-color:var(--primary);background:color-mix(in srgb,var(--primary) 8%,var(--card))}
.waAyNo{font-size:11px;font-weight:900;color:var(--accent);margin-bottom:6px}.waLbl{font-size:10px;color:var(--muted);font-weight:800}.waTx{font-family:'Traditional Arabic','Amiri',serif;font-size:21px;line-height:1.9;margin:3px 0}
.waTx mark{background:#c9a34b55;color:inherit;padding:0 2px;border-radius:3px}.waCk{display:flex;align-items:center;gap:8px;margin-top:8px;font-size:13px;font-weight:800}.waNote{width:100%;margin-top:7px;border:1px solid var(--line);border-radius:10px;padding:8px;font:inherit;font-size:12px;background:var(--bg);color:var(--ink)}
`;
const st=document.createElement('style');st.textContent=css;document.head.appendChild(st);

function waLoad(){try{const d=JSON.parse(localStorage.getItem(WA_KEY));if(d&&d.surahs)return d}catch(e){}return{v:1,reviewer:'',surahs:{}}}
function waSave(d){try{localStorage.setItem(WA_KEY,JSON.stringify(d))}catch(e){}}
function waEsc(s){return String(s).replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]))}
function waScope(){return localStorage.getItem(WA_SCOPE)==='verified'?'verified':'all'}

function waData(n){
  const s=window.WARSH_M&&WARSH_M[+n];if(!s)return null;
  let hafs=[];
  if(typeof bySura!=='undefined'&&bySura.get)hafs=(bySura.get(+n)||[]).filter(x=>x[1]>0);
  else if(typeof AYAHS!=='undefined')hafs=AYAHS.filter(r=>r[0]===+n&&r[1]>0);
  return{s,hafs};
}
function waDiffs(n){const d=waData(n);if(!d)return null;const out=[];d.s.ay.forEach((t,i)=>{const h=(d.hafs[i]&&d.hafs[i][7])||'';if(h!==t)out.push({a:i+1,h,t})});return out}
function waMark(a,b){let i=0;while(i<a.length&&i<b.length&&a[i]===b[i])i++;let j=0;while(j<a.length-i&&j<b.length-i&&a[a.length-1-j]===b[b.length-1-j])j++;const mk=s=>waEsc(s.slice(0,i))+'<mark>'+waEsc(s.slice(i,s.length-j))+'</mark>'+waEsc(s.slice(s.length-j));return[mk(a),mk(b)]}

let waRoot=null;
function warshAuditOpen(){
  if(!window.WARSH_M||!Object.keys(WARSH_M).length){alert('حمّل warsh.js أولاً');return}
  if(waRoot)waRoot.remove();
  waRoot=document.createElement('div');waRoot.className='waWrap';
  waRoot.innerHTML='<div class="waBox"><div class="waHead"><h3>📖 تدقيق مصحف ورش</h3><button class="waClose">✕</button></div><div class="waBody" id="waBody"></div></div>';
  document.body.appendChild(waRoot);waRoot.addEventListener('click',e=>{if(e.target===waRoot)waClose()});waRoot.querySelector('.waClose').onclick=waClose;waDash();
}
function waClose(){if(waRoot){waRoot.remove();waRoot=null}}

function waDash(){
  const data=waLoad(),body=waRoot.querySelector('#waBody');let loaded=0,ver=0,prog=0,diffTotal=0,ckTotal=0,cells='';
  for(let n=1;n<=114;n++){
    const s=WARSH_M[n];if(!s){cells+='<button class="waCell off" disabled title="غير محمّلة">'+n+'</button>';continue}
    loaded++;const dn=(waDiffs(n)||[]).length;diffTotal+=dn;const rec=data.surahs[n]||{};const ck=rec.checked?Object.values(rec.checked).filter(Boolean).length:0;ckTotal+=ck;
    let cls='waCell';if(rec.status==='verified'){ver++;cls+=' done'}else if(ck>0||rec.ack){prog++;cls+=' prog'}
    cells+='<button class="'+cls+'" data-n="'+n+'" title="فروق رقمية: '+dn+'">'+n+'</button>';
  }
  const pct=loaded?Math.round(ver/loaded*100):0;
  body.innerHTML='<div class="waWarn">⚠️ الأداة تُظهر <b>الفروق الرقمية فقط</b> بين نسختك ونسخة حفص لتسريع المطابقة على <b>مصحف مطبوع موثوق</b> — هي لا تُغني عن المراجعة البشرية. علِّم ✓ بعد التأكد بعينك على المصحف.</div>'
  +'<div class="waStats"><div class="waStat"><b>'+ver+' / '+loaded+'</b><span>سور مُراجَعة</span></div><div class="waStat"><b>'+diffTotal+'</b><span>مواضع فرق (المحمّلة)</span></div><div class="waStat"><b>'+ckTotal+'</b><span>مواضع تمّت مطابقتها</span></div></div>'
  +'<div class="waBar"><i style="width:'+pct+'%"></i></div><div class="waTools"><input class="waRev" id="waRev" placeholder="اسم المراجع" value="'+waEsc(data.reviewer||'')+'"><select class="waSel" id="waScopeSel"><option value="all"'+(waScope()==='all'?' selected':'')+'>القارئ يعرض كل سور ورش</option><option value="verified"'+(waScope()==='verified'?' selected':'')+'>القارئ: المُراجَعة فقط</option></select><button class="waBtn soft" id="waExport">⬇ تصدير تقرير</button></div><div class="waGrid">'+cells+'</div>';
  body.querySelector('#waRev').onchange=e=>{const d=waLoad();d.reviewer=e.target.value.trim();waSave(d)};
  body.querySelector('#waScopeSel').onchange=e=>{localStorage.setItem(WA_SCOPE,e.target.value);if(typeof toast==='function')toast(e.target.value==='verified'?'🔒 القارئ: سور ورش المُراجَعة فقط':'القارئ: كل سور ورش المتوفرة')};
  body.querySelector('#waExport').onclick=waExport;body.querySelectorAll('.waCell[data-n]').forEach(b=>b.onclick=()=>waSurah(+b.dataset.n));
}

function waSurah(n){
  const data=waLoad();const rec=data.surahs[n]||(data.surahs[n]={status:'none',mode:'diff',checked:{},notes:{},ack:false,date:''});const d=waData(n);if(!d)return;
  const diffs=waDiffs(n)||[],S=SURAHS[n-1],full=rec.mode==='full',body=waRoot.querySelector('#waBody');let rows='';
  const mkRow=(a,h,t)=>{const ck=!!rec.checked[a],note=rec.notes[a]||'',hh=waMark(h,t);rows+='<div class="waRow'+(ck?' ok':'')+'" data-a="'+a+'"><div class="waAyNo">الآية '+a+(h===t?' <span style="color:var(--muted);font-weight:600">(مطابقة لحفص رقمياً)</span>':'')+'</div><div class="waLbl">حفص:</div><div class="waTx">'+(hh[0]||'—')+'</div><div class="waLbl">ورش:</div><div class="waTx">'+(hh[1]||'—')+'</div><label class="waCk"><input type="checkbox" class="waCkBox" '+(ck?'checked':'')+'> ✓ راجعتُ هذا الموضع على المصحف المطبوع</label><input class="waNote" placeholder="ملاحظة (اختياري)" value="'+waEsc(note)+'"></div>'};
  if(full){d.s.ay.forEach((t,i)=>mkRow(i+1,(d.hafs[i]&&d.hafs[i][7])||'',t))}
  else{diffs.forEach(x=>mkRow(x.a,x.h,x.t));if(!diffs.length)rows='<div class="waWarn">لا فروق رقمية مع حفص في هذه السورة — راجع عيّنة ثم أكِّد بالأسفل.</div>';rows+='<label class="waCk" style="margin:10px 0"><input type="checkbox" id="waAck" '+(rec.ack?'checked':'')+'> أكِّد: راجعتُ عيّنة من الآيات المتطابقة مع حفص</label>'}
  body.innerHTML='<div class="waTools"><button class="waBtn soft" id="waBack">‹ كل السور</button><select class="waSel" id="waMode"><option value="diff"'+(full?'':' selected')+'>مراجعة الفروق فقط</option><option value="full"'+(full?' selected':'')+'>مراجعة السورة كاملة</option></select><button class="waBtn soft" id="waResetS">تصفير</button></div><div style="font-weight:900;margin:2px 0 10px">سورة '+S.name+' — '+(full?d.s.ay.length+' آية':diffs.length+' موضع فرق')+'</div>'+rows+'<button class="waBtn" id="waSaveS" style="width:100%;margin-top:6px">حفظ حالة المراجعة</button>';
  body.querySelector('#waBack').onclick=waDash;body.querySelector('#waMode').onchange=e=>{rec.mode=e.target.value;waSave(data);waSurah(n)};body.querySelector('#waResetS').onclick=()=>{if(confirm('تصفير مراجعة هذه السورة؟')){delete data.surahs[n];waSave(data);waSurah(n)}};body.querySelectorAll('.waCkBox').forEach(cb=>cb.onchange=()=>cb.closest('.waRow').classList.toggle('ok',cb.checked));
  body.querySelector('#waSaveS').onclick=()=>{body.querySelectorAll('.waRow').forEach(r=>{const a=+r.dataset.a;rec.checked[a]=r.querySelector('.waCkBox').checked;const nt=r.querySelector('.waNote').value.trim();if(nt)rec.notes[a]=nt;else delete rec.notes[a]});if(!full){const ack=body.querySelector('#waAck');rec.ack=ack?ack.checked:false}let done;if(full)done=d.s.ay.length>0&&Array.from({length:d.s.ay.length},(_,i)=>i+1).every(a=>rec.checked[a]);else done=diffs.length?(diffs.every(x=>rec.checked[x.a])&&!!rec.ack):!!rec.ack;const anyCk=Object.values(rec.checked).some(Boolean)||rec.ack;rec.status=done?'verified':(anyCk?'inprogress':'none');rec.date=done?new Date().toISOString().slice(0,10):'';waSave(data);if(typeof toast==='function')toast(done?'✅ '+S.name+': مُراجَعة'+(full?' كاملة':' (الفروق)')+' — '+(data.reviewer||''):'تم حفظ التقدم');waSurah(n)};
}

function waExport(){
  const data=waLoad(),lines=['تقرير تدقيق مصحف ورش — تطبيق وِرد','التاريخ: '+new Date().toISOString().slice(0,10),'المراجع: '+(data.reviewer||'—'),''];let v=0;
  for(let n=1;n<=114;n++){const rec=data.surahs[n];if(!rec||rec.status==='none')continue;if(rec.status==='verified')v++;const dn=(waDiffs(n)||[]).length;lines.push('سورة '+SURAHS[n-1].name+' ('+n+') — '+(rec.status==='verified'?'مُراجَعة ✓':'قيد المراجعة')+(rec.date?' في '+rec.date:'')+' — وضع: '+(rec.mode==='full'?'كاملة':'الفروق')+' — فروق رقمية: '+dn);Object.keys(rec.notes||{}).forEach(a=>lines.push('   • آية '+a+': '+rec.notes[a]))}
  lines.push('','الإجمالي المُراجَع: '+v+' من '+Object.keys(WARSH_M).length+' سورة محمّلة');const txt=lines.join('\n');
  const show=()=>{waClose();const w=document.createElement('div');w.className='waWrap';w.innerHTML='<div class="waBox" style="border-radius:22px"><div class="waHead"><h3>التقرير</h3><button class="waClose">✕</button></div><div class="waBody"><textarea class="waNote" style="height:46dvh;font-size:11px" readonly></textarea><button class="waBtn" style="width:100%;margin-top:10px" id="waCopy">نسخ</button></div></div>';document.body.appendChild(w);w.querySelector('textarea').value=txt;w.querySelector('.waClose').onclick=()=>w.remove();w.addEventListener('click',e=>{if(e.target===w)w.remove()});w.querySelector('#waCopy').onclick=()=>{const ta=w.querySelector('textarea');ta.select();try{document.execCommand('copy');if(typeof toast==='function')toast('✓ تم النسخ')}catch(e){}}};
  if(navigator.clipboard&&navigator.clipboard.writeText)navigator.clipboard.writeText(txt).then(()=>{if(typeof toast==='function')toast('✓ نُسخ التقرير للحافظة')}).catch(show);else show();
}

window.warshAuditOpen=warshAuditOpen;
window.waDiffs=waDiffs;
window.warshAuditAllows=function(sura){
  if(localStorage.getItem(WA_SCOPE)!=='verified')return true;
  const d=waLoad(),rec=d&&d.surahs&&d.surahs[+sura];
  return !!(rec&&rec.status==='verified');
};

const orig=window.usingWarshSura;
if(typeof orig==='function'){
  window.usingWarshSura=function(sura){
    if(!window.warshAuditAllows(sura))return false;
    return orig(sura);
  };
}
})();
