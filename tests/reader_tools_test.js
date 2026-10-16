const fs=require('fs'),path=require('path'),assert=require('assert');
const {JSDOM}=require('jsdom'),{indexedDB}=require('fake-indexeddb');
const dom=new JSDOM('<div id="sheetHost"></div><div id="modalHost"></div>',{url:'https://appassets.androidplatform.net',runScripts:'outside-only'}),w=dom.window;
Object.assign(w,{indexedDB,Blob:global.Blob,AbortController:global.AbortController,state:{riwaya:'hafs'},bySura:new Map([[1,[[1,1],[1,2]]]]),SURAHS:[{name:'الفاتحة'}],reciterInfo:()=>({id:'test',name:'قارئ الاختبار',audiotype:0,url:'https://audio.test/'}),esc:x=>x,arNum:x=>x,toast:()=>{},closeSheet:()=>{w.document.getElementById('sheetHost').innerHTML=''},closeModal:()=>{},openAyaSheet:()=>{}});
w.sheetHost=w.document.getElementById('sheetHost');w.modalHost=w.document.getElementById('modalHost');
let requests=[],mode='ok';w.fetch=async url=>{requests.push(url);if(mode==='offline')throw Error('Offline');if(url.endsWith('.mp3')){const bytes=new Uint8Array(512);bytes.set([73,68,51]);return {ok:true,blob:async()=>new Blob([bytes])}}
return {ok:true,blob:async()=>new Blob([JSON.stringify({chapter:mode==='invalid'?[{chapter:2,verse:1,text:'wrong'}]:[{chapter:1,verse:1,text:'Translation one'},{chapter:1,verse:2,text:'Translation two'}]})])}};
w.eval(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/wird_reader_tools.js'),'utf8'));
const wait=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 await w.downloadHafsSurahV7577(1);assert.equal(requests.length,2);assert.equal((await w.hafsCachedAudioV7577('test',1,2)).size,512);assert.equal(await w.hafsCachedAudioV7577('other',1,2),null);
 mode='offline';await w.downloadHafsSurahV7577(1);assert.equal(requests.length,2);assert(w.sheetHost.textContent.includes('اكتمل'));
 await w.deleteHafsSurahV7577(1);assert.equal(await w.hafsCachedAudioV7577('test',1,1),null);
 mode='ok';w.showTranslationV7577(1,2);await wait(100);assert.equal(w.document.getElementById('translationBodyV7577').textContent,'Translation two');const count=requests.length;
 mode='offline';w.showTranslationV7577(1,1);await wait(100);assert.equal(w.document.getElementById('translationBodyV7577').textContent,'Translation one');assert.equal(requests.length,count);
 mode='invalid';const pick=w.document.getElementById('translationEditionV7577');pick.value='fra-muhammadhamidul';pick.onchange();await wait(100);assert(w.document.getElementById('translationBodyV7577').textContent.includes('تعذر'));
 w.setHafsRepeatV7577(3);assert.equal(w.state.repeatCount,3);assert.equal(w.localStorage.getItem('wird_hafs_repeat'),'3');
 w.state.riwaya='warsh';w.modalHost.innerHTML='unchanged';w.showTranslationV7577(1,1);assert.equal(w.modalHost.innerHTML,'unchanged');
 w.state.riwaya='hafs';let release;w.fetch=()=>new Promise(r=>release=r);const pending=w.downloadHafsSurahV7577(1);await wait(50);w.cancelHafsDownloadV7577();const bytes=new Uint8Array(512);bytes.set([73,68,51]);release({ok:true,blob:async()=>new Blob([bytes])});await pending;assert.equal(await w.hafsCachedAudioV7577('test',1,1),null);
 console.log('Offline audio, resume, reciter isolation, deletion, cancellation, translation schema/cache and Warsh isolation passed');dom.window.close();
})().catch(e=>{console.error(e);dom.window.close();process.exitCode=1});
