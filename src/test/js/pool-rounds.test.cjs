const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/app.js'),'utf8');
const pool=source.slice(source.indexOf('function poolPreview('),source.indexOf("$('#bracket-area').addEventListener"));
const render=source.slice(source.indexOf('function renderBrackets('),source.indexOf('function field('));
for(const size of [4,8,16]){
 const athletes=Array.from({length:size},(_,i)=>({id:101+i,name:'Участник '+(i+1),club:'Команда '+i,coach:''}));
 const bouts=[];let id=1;
 for(let round=1,count=size/2;count>=1;round++,count/=2)for(let position=0;position<count;position++)bouts.push({id:id++,categoryId:1,roundNumber:round,position,redId:round===1?101+position*2:null,blueId:round===1?102+position*2:null,resolved:false,automatic:false,winnerId:null});
 const max=Math.log2(size);bouts.push({id:id++,categoryId:1,roundNumber:max+1,position:0,redId:null,blueId:null,resolved:false,automatic:false,winnerId:null,bronze:true});
 const area={innerHTML:''};const context={current:1,state:{tournament:{name:'Тест',eventDate:'2026-10-31',drawSeed:42},categories:[{id:1,name:'Категория'}],athletes,bouts,podium:{},assignments:Object.fromEntries(athletes.map(a=>[a.id,1]))},printSettings:{tatami:'A',banner:true,timeSlot:'',chiefJudge:'',secretary:''},count:()=>size,esc:v=>String(v??''),$:s=>s==='#bracket-area'?area:s==='#bracket-area .result-controls'?{open:true}:{value:'1'}};
 vm.createContext(context);vm.runInContext(pool+render,context);
 let played=0;
 while(true){
  const ready=bouts.filter(b=>!b.resolved&&b.redId&&b.blueId);if(!ready.length)break;
  const svg=context.poolPreview(1,bouts);
  for(const b of ready)for(const participant of [b.redId,b.blueId])assert(svg.includes(`data-bout="${b.id}" data-winner="${participant}"`),`Size ${size}: participant ${participant} is not selectable in round ${b.roundNumber}, bout ${b.id}`);
  context.renderBrackets();assert(area.innerHTML.includes('<details class="result-controls" open>'),'The result cards must remain expanded after updating a winner');
  const b=ready[0];b.winnerId=b.redId;b.resolved=true;played++;
  if(!b.bronze&&b.roundNumber<max){const parent=bouts.find(p=>!p.bronze&&p.roundNumber===b.roundNumber+1&&p.position===Math.floor(b.position/2));parent[b.position%2===0?'redId':'blueId']=b.winnerId;}
  const semis=bouts.filter(b=>!b.bronze&&b.roundNumber===max-1),bronze=bouts.find(b=>b.bronze);
  if(semis.every(b=>b.resolved)){bronze.redId=semis[0].blueId;bronze.blueId=semis[1].blueId;}
 }
 assert.equal(played,size);assert(bouts.every(b=>b.resolved));
}
console.log('PASS: both participants remain selectable through every round, final and bronze bout in 4-, 8- and 16-athlete pools; expanded cards stay open.');
