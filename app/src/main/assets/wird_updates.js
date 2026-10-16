(function(root){
 'use strict';
 const CURRENT='75.81',URL='https://api.github.com/repos/brazilimaroki-sketch/Wird-Downloads/releases/latest',CACHE='wird_update_cache_v7574';
 function version(value){const s=String(value||'').trim().replace(/^v/i,'');return /^\d+(\.\d+){1,3}$/.test(s)?s:null;}
 function compare(a,b){a=version(a);b=version(b);if(!a||!b)return 0;const x=a.split('.').map(Number),y=b.split('.').map(Number);for(let i=0;i<Math.max(x.length,y.length);i++){if((x[i]||0)!==(y[i]||0))return (x[i]||0)>(y[i]||0)?1:-1}return 0;}
 function releaseNotice(release,current){const tag=version(release?.tag_name),apk=release?.assets?.some(x=>x.state==='uploaded'&&Number(x.size)>0&&/\.apk$/i.test(x.name||''));return {available:!!(tag&&apk&&!release.draft&&!release.prerelease&&compare(tag,current)>0),version:tag||''};}
 const api={version,compare,releaseNotice};if(typeof module!=='undefined')module.exports=api;
 if(!root.document)return;
 let checking=false,manual=false;
 function current(){try{return JSON.parse(root.WirdNative.getAppVersion()).versionName||CURRENT}catch{return CURRENT}}
 root.onAppUpdateAvailableV7574=function(notice){checking=false;let old=document.getElementById('updateNoticeV7574');if(old)old.remove();if(!notice?.available||compare(notice.version,current())<=0){if(manual)root.toast?.(notice?.checked?'التطبيق محدّث':'تعذر التحقق من التحديث. جرّب لاحقاً.');manual=false;return}if(!manual&&localStorage.getItem('wird_update_dismissed_v7574')===notice.version)return;
  const box=document.createElement('aside');box.id='updateNoticeV7574';box.className='updateNoticeV7574';box.setAttribute('role','status');const label=document.createElement('strong');label.textContent='تحديث وِرد '+notice.version+' متوفر';box.appendChild(label);
  const download=document.createElement('button');download.className='updateDownload';download.textContent='فتح موقع التحميل';download.onclick=()=>root.openUpdateWebsiteV103();box.appendChild(download);
  const dismiss=document.createElement('button');dismiss.textContent='لاحقاً';dismiss.onclick=()=>{localStorage.setItem('wird_update_dismissed_v7574',notice.version);box.remove()};box.appendChild(dismiss);document.getElementById('content')?.insertAdjacentElement('beforebegin',box);manual=false;
 };
 root.checkAppUpdateV7574=async function(force=false){if(checking)return;checking=true;manual=!!force;
  if(root.WirdNative&&typeof root.WirdNative.checkAppUpdate==='function'){try{root.WirdNative.checkAppUpdate(!!force);return}catch{checking=false;manual=false;return}}
  let timer;try{let cached;try{cached=JSON.parse(localStorage.getItem(CACHE)||'null')}catch{}if(!force&&cached&&Date.now()-cached.time<21600000){root.onAppUpdateAvailableV7574({...releaseNotice(cached.release,current()),checked:true});return}
   const abort=new AbortController();timer=setTimeout(()=>abort.abort(),10000);const response=await fetch(URL,{signal:abort.signal,headers:{Accept:'application/vnd.github+json'}});if(!response.ok)throw Error('update check');const release=await response.json();localStorage.setItem(CACHE,JSON.stringify({time:Date.now(),release}));root.onAppUpdateAvailableV7574({...releaseNotice(release,current()),checked:true});
  }catch{checking=false;manual=false;if(force)root.toast?.('تعذر التحقق من التحديث. جرّب لاحقاً.')}finally{clearTimeout(timer)}
 };
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)root.checkAppUpdateV7574(false)});setTimeout(()=>root.checkAppUpdateV7574(false),2000);
})(typeof window==='undefined'?globalThis:window);
