const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/app.js'),'utf8');
const pool=source.slice(source.indexOf('function poolPreview('),source.indexOf("$('#bracket-area').addEventListener"));
const render=source.slice(source.indexOf('function renderBrackets('),source.indexOf('function field('));
const athletes=[{id:10,name:'Получатель прохода',club:'Клуб A',coach:''},{id:20,name:'Соперник один',club:'Клуб Б',coach:''},{id:30,name:'Соперник два',club:'Клуб В',coach:''}];
const bouts=[{id:1,categoryId:1,roundNumber:1,position:0,redId:10,blueId:null,winnerId:10,resolved:true,automatic:true},
 {id:2,categoryId:1,roundNumber:1,position:1,redId:20,blueId:30,winnerId:null,resolved:false,automatic:false},
 {id:3,categoryId:1,roundNumber:2,position:0,redId:10,blueId:null,winnerId:null,resolved:false,automatic:false},
 {id:4,categoryId:1,roundNumber:3,position:0,redId:null,blueId:null,winnerId:null,resolved:false,automatic:true,bronze:true}];
const area={innerHTML:''};
const context={state:{tournament:{name:'Тест',eventDate:'2026-10-31',drawSeed:42},categories:[{id:1,name:'Категория'}],athletes,bouts,podium:{},assignments:{10:1,20:1,30:1}},current:1,
 printSettings:{tatami:'A',banner:true,timeSlot:'',chiefJudge:'',secretary:''},count:()=>3,esc:v=>String(v??''),$:s=>s==='#bracket-area'?area:{value:'1'}};
vm.createContext(context);vm.runInContext(pool+render,context);
let svg=context.poolPreview(1,bouts);
assert(!svg.includes('Свободный проход'));
assert.equal(svg.split('Получатель прохода').length-1,1);
assert(svg.includes('x="170"'), 'The bye recipient enters at the next round column');
assert(!svg.includes('>A1</text>'));assert(!svg.includes('>A4</text>'));
context.renderBrackets();assert(!area.innerHTML.includes('data-bout="1"'));assert(!area.innerHTML.includes('data-bout="4"'));
// Completing the other semifinal makes the next actual fight selectable.
bouts[1].resolved=true;bouts[1].winnerId=20;bouts[2].blueId=20;
svg=context.poolPreview(1,bouts);assert(svg.includes('data-bout="3" data-winner="10"'));
assert(svg.includes('data-bout="3" data-winner="20"'),'An ordinary winner must also be selectable in the final');
console.log('PASS: no phantom bye pair in SVG or cards; recipient is in the next round and can fight when opponent is ready.');
