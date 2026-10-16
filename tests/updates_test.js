const assert=require('node:assert/strict'),path=require('node:path');
const U=require(path.join(__dirname,'../app/src/main/assets/wird_updates.js'));
assert(U.compare('v75.75','75.74')>0);assert(U.compare('75.100','75.99')>0);assert(U.compare('75.74','75.74')===0);assert(U.compare('75.73','75.74')<0);assert(U.compare('75.74.1','75.74')>0);assert(U.compare('v75.75-beta','75.74')===0);
const release={tag_name:'v75.75',draft:false,prerelease:false,assets:[{name:'Wird.apk',state:'uploaded',size:1000}]};
assert(U.releaseNotice(release,'75.74').available);assert(!U.releaseNotice(release,'75.75').available);assert(!U.releaseNotice({...release,draft:true},'75.74').available);assert(!U.releaseNotice({...release,prerelease:true},'75.74').available);assert(!U.releaseNotice({...release,assets:[]},'75.74').available);assert(!U.releaseNotice({...release,tag_name:'invalid'},'75.74').available);assert(!U.releaseNotice({...release,assets:[{name:'Project.zip',state:'uploaded',size:1}]},'75.74').available);
console.log('Version ordering, equal/older releases, invalid tags, drafts/prereleases and APK requirement passed');
