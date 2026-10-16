(function(root){
 'use strict';
 const normalize=s=>String(s||'').normalize('NFD').replace(/[\u064B-\u065F\u0670\u06D6-\u06EDـ]/g,'').replace(/[أإآٱ]/g,'ا').replace(/ؤ/g,'و').replace(/ئ/g,'ي').replace(/ى/g,'ي').replace(/ة/g,'ه').replace(/[^\u0621-\u064A\s]/g,' ').replace(/\s+/g,' ').trim();
 const words=s=>normalize(s).split(' ').filter(Boolean);
 function create(verses){
  const tokens=[],issues=verses.map(()=>[]),read=verses.map(()=>0);
  verses.forEach((v,vi)=>words(v.text).forEach((word,wi)=>tokens.push({word,vi,wi})));
  let cursor=0,pending=[],history=[],lastChunk='';
  function feed(text){
   let incoming=words(text);if(!incoming.length||cursor>=tokens.length)return;
   const key=incoming.join(' '),atCurrent=incoming.every((w,i)=>tokens[cursor+i]?.word===w);
   // Repeated/cumulative recognition results are not fresh recitation.
   if(key===lastChunk&&!atCurrent)return;
   lastChunk=key;
   if(!atCurrent){
    for(let k=Math.min(history.length,incoming.length);k>0;k--){
     if(history.slice(-k).every((w,i)=>incoming[i]===w)){incoming=incoming.slice(k);break;}
    }
   }
   if(!incoming.length)return;
   if(atCurrent)pending=[];
   if(pending.length&&incoming.length>=pending.length&&pending.every((w,i)=>incoming[i]===w))pending=[];
   const heard=pending.concat(incoming).slice(-64);pending=heard;

   // Bound each alignment to this speech segment; leave future words ungraded.
   const target=tokens.slice(cursor,cursor+heard.length+16),n=heard.length,m=target.length;
   const d=Array.from({length:n+1},()=>Array(m+1).fill(0));
   for(let i=0;i<=n;i++)d[i][0]=i*3;for(let j=0;j<=m;j++)d[0][j]=j*2;
   for(let i=1;i<=n;i++)for(let j=1;j<=m;j++)d[i][j]=Math.min(d[i-1][j-1]+(heard[i-1]===target[j-1].word?0:3),d[i-1][j]+3,d[i][j-1]+2);
   let end=0;for(let j=1;j<=m;j++)if(d[n][j]<d[n][end]||(d[n][j]===d[n][end]&&Math.abs(j-n)<=Math.abs(end-n)))end=j;
   let i=n,j=end;const ops=[];
   while(i||j){
    if(i&&j&&d[i][j]===d[i-1][j-1]+(heard[i-1]===target[j-1].word?0:3)){ops.push({t:target[j-1],type:heard[i-1]===target[j-1].word?'match':'different',heard:heard[--i]});j--;}
    else if(j&&d[i][j]===d[i][j-1]+2){ops.push({t:target[--j],type:'missing'});}
    else{ops.push({t:target[Math.min(j,m-1)],type:'extra',heard:heard[--i]});}
   }
   ops.reverse();
   let run=0,bestRun=0,matches=0;
   ops.forEach(o=>{if(o.type==='match'){matches++;run++;bestRun=Math.max(bestRun,run)}else run=0;});
   const anchoredAtStart=ops[0]?.type==='match'&&ops[0].t===target[0];
   // An unrelated or repeated phrase must not consume unseen verses.
   if(!end||!matches||(!anchoredAtStart&&bestRun<2)||matches<Math.ceil(heard.length*.4))return;
   pending=[];history=history.concat(heard).slice(-64);
   ops.forEach(o=>{if(o.type!=='match')issues[o.t.vi].push({type:o.type,expected:o.type==='extra'?'':o.t.word,heard:o.heard||''});if(o.type!=='extra')read[o.t.vi]=Math.max(read[o.t.vi],o.t.wi+1);});
   cursor+=end;
  }
  function completed(){return verses.filter((v,i)=>read[i]>=words(v.text).length);}
  function partial(){const t=tokens[cursor];if(!t)return '';return words(verses[t.vi].text).slice(0,read[t.vi]).join(' ');}
  function skipVerse(){pending=[];lastChunk='';const t=tokens[cursor];if(!t)return;while(cursor<tokens.length&&tokens[cursor].vi===t.vi){const x=tokens[cursor++];issues[x.vi].push({type:'missing',expected:x.word,heard:''});read[x.vi]=x.wi+1;}}
  return {feed,completed,partial,skipVerse,isComplete:()=>cursor>=tokens.length,report:()=>verses.map((v,i)=>({...v,issues:issues[i].concat(tokens[cursor]?.vi===i?pending.map(heard=>({type:'unconfirmed',expected:'',heard})):[]),unread:read[i]<words(v.text).length}))};
 }
 const api={normalize,create};if(typeof module!=='undefined')module.exports=api;root.WirdMemory=api;
})(typeof window!=='undefined'?window:globalThis);
