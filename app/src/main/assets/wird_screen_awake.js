(()=>{
'use strict';
let last;
function sync(){
 const reader=document.getElementById('readerView'),home=document.getElementById('homeView');
 const reading=reader&&!reader.classList.contains('hidden')&&getComputedStyle(reader).display!=='none';
 const adhkar=home&&!home.classList.contains('hidden')&&typeof state!=='undefined'&&['azkar','tasbih'].includes(state.tab);
 const enabled=!document.hidden&&!!(reading||adhkar);
 if(enabled===last)return;
 try{if(window.WirdNative&&typeof WirdNative.setReadingScreenOn==='function'){WirdNative.setReadingScreenOn(enabled);last=enabled}}catch(e){console.warn('[Wird reading screen]',e)}
}
function init(){
 const observer=new MutationObserver(sync);
 for(const id of ['readerView','homeView']){const el=document.getElementById(id);if(el)observer.observe(el,{attributes:true,attributeFilter:['class','style']})}
 const content=document.getElementById('content');if(content)observer.observe(content,{childList:true});
 document.addEventListener('visibilitychange',sync);window.addEventListener('pagehide',()=>{try{WirdNative.setReadingScreenOn(false);last=false}catch(e){}});
 window.addEventListener('pageshow',sync);sync();
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
