const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const belts=['Белый','Оранжевый','Синий','Жёлтый','Зелёный','Коричневый','Чёрный'], colors=['#fff','#e59838','#537cbb','#e8c843','#638d56','#846247','#30332b'];
let state=null,current=null,view='overview',formHandler;
let printSettings={tatami:'A',timeSlot:'',chiefJudge:'',secretary:'',banner:true}, applicationBatches=[], importRows=[], importTournament=null, importVerified=false;
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function api(path='',method='GET',body){const r=await fetch('/api/tournaments'+path,{method,headers:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});if(!r.ok){let e;try{e=await r.json()}catch{e={message:'Ошибка сервера: '+r.status}}throw Error(e.message||'Не удалось выполнить запрос')}const text=await r.text();return text?JSON.parse(text):null}
function toast(message){$('#toast').textContent=message;$('#toast').hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('#toast').hidden=true,5000)}
async function safe(fn){try{await fn()}catch(e){toast(e.message)}}
async function list(selectId){const ts=await api();$('#tournament-select').innerHTML=ts.length?ts.map(t=>`<option value="${t.id}">${esc(t.name)}</option>`).join(''):'<option>Нет турниров</option>';if(ts.length){current=selectId||ts[0].id;$('#tournament-select').value=current;await load()}else{$('#workspace').hidden=true;$('#welcome').hidden=false}}
async function load(){[state,printSettings,applicationBatches]=await Promise.all([api('/'+current),api('/'+current+'/print-settings'),api('/'+current+'/applications')]);render();renderApplications()}
function switchView(v){view=v;$$('.view').forEach(x=>x.hidden=x.id!==v);$$('[data-view]').forEach(x=>x.classList.toggle('active',x.dataset.view===v));if(state)renderBrackets()}
function age(a){let d=state.tournament.eventDate,b=a.birthDate;return Number(d.slice(0,4))-Number(b.slice(0,4))-(d.slice(5)<b.slice(5)?1:0)}
function assigned(a){return state.categories.find(c=>c.id===state.assignments[a.id])}
function count(c){return state.athletes.filter(a=>state.assignments[a.id]===c.id).length}
const range=c=>`${c.sex==='M'?'Мужской':'Женский'} · ${c.minAge}–${c.maxAge} лет · свыше ${c.minWeight} до ${c.maxWeight} кг`;
function render(){const t=state.tournament,drawn=t.drawSeed!==null;$('#workspace').hidden=false;$('#welcome').hidden=true;$('#title').textContent=t.name;$('#crumb').textContent=t.name;$('#subtitle').textContent=new Date(t.eventDate+'T12:00:00').toLocaleDateString('ru-RU',{day:'numeric',month:'long',year:'numeric'})+(t.venue?' · '+t.venue:'');$('#athlete-count').textContent=state.athletes.length;$('#side-count').textContent=state.athletes.length;$('#category-count').textContent=state.categories.length;$('#assigned-count').textContent=state.athletes.length-state.unassigned.length;$('#unassigned-count').textContent=state.unassigned.length?state.unassigned.length+' без категории':'все участники распределены';const real=state.bouts.filter(b=>!b.automatic);$('#bout-count').innerHTML=real.filter(b=>b.resolved).length+` <em>/ ${real.length}</em>`;$('#draw-status').textContent=drawn?'жеребьёвка проведена':'жеребьёвка не проведена';$('#steps').innerHTML=[[state.categories.length>0,'Задайте категории','Возраст, вес и пояс'],[state.athletes.length>0&&state.unassigned.length===0,'Зарегистрируйте участников',state.athletes.length+' спортсменов в заявке'],[drawn,'Проведите жеребьёвку','Сетки с автоматическим продвижением']].map((x,i)=>`<div class="step ${x[0]?'done':''}"><b>${x[0]?'✓':'0'+(i+1)}</b><div><strong>${x[1]}</strong><small>${x[2]}</small></div></div>`).join('');$('#overview-categories').innerHTML=state.categories.length?state.categories.slice(0,4).map((c,i)=>`<div class="category-row"><span class="number">0${i+1}</span><div><strong>${esc(c.name)}</strong><small>${esc(range(c))}</small></div><span class="pill">${count(c)} участников</span></div>`).join(''):'<div class="empty">Добавьте первую категорию по регламенту турнира.</div>';$('#category-cards').innerHTML=state.categories.map(c=>`<article class="category-card"><div class="top"><span class="pill">${c.sex==='M'?'Мужская':'Женская'}</span><button class="text-button" data-delete-category="${c.id}" ${drawn?'disabled':''} aria-label="Удалить категорию">×</button></div><h3>${esc(c.name)}</h3><p>${c.minAge}–${c.maxAge} лет · свыше ${c.minWeight} до ${c.maxWeight} кг</p><p>${belts[c.minBelt]} — ${belts[c.maxBelt]} пояс</p><div class="meta"><span>${count(c)} участников</span><span>${drawn?'Сетка создана':'Ожидает жеребьёвки'}</span></div></article>`).join('')||'<div class="empty">Категорий пока нет.</div>';['add-category','add-athlete'].forEach(id=>$('#'+id).disabled=drawn);$('#draw').hidden=drawn;$('#reset').hidden=!drawn;$('#hero-draw').textContent=drawn?'Открыть сетки ↗':'Провести жеребьёвку ↗';$('#seed-info').textContent=drawn?'Жеребьёвка сохранена. Разведение команд применяется при создании новых сеток.':'Одноклубники равномерно разводятся по половинам и четвертям сетки.';const chosen=$('#bracket-category').value;$('#bracket-category').innerHTML=state.categories.map(c=>`<option value="${c.id}">${esc(c.name)} (${count(c)})</option>`).join('');if(state.categories.some(c=>String(c.id)===chosen))$('#bracket-category').value=chosen;renderAthletes();switchView(view);if($('#import-application'))$('#import-application').disabled=drawn;renderApplications()}
function renderAthletes(){if(!state)return;const q=$('#search').value.toLowerCase();const as=state.athletes.filter(a=>(a.name+' '+a.club).toLowerCase().includes(q)&&(!$('#only-unassigned').checked||!assigned(a)));$('#athlete-rows').innerHTML=as.map(a=>`<tr><td><strong>${esc(a.name)}</strong><small>${esc(a.club)||'Без клуба'} · ${a.sex==='M'?'М':'Ж'}</small></td><td>${age(a)} лет</td><td>${a.weight} кг</td><td><i class="belt" style="background:${colors[a.belt]}"></i>${belts[a.belt]}</td><td><span class="pill ${assigned(a)?'':'warn'}">${esc(assigned(a)?.name||'Нет категории')}</span></td><td><button class="text-button" data-delete-athlete="${a.id}" ${state.tournament.drawSeed!==null?'disabled':''} aria-label="Удалить участника">×</button></td></tr>`).join('')||'<tr><td colspan="6" class="empty">Участники не найдены.</td></tr>'}
function renderBrackets(){
 if(!state)return;
 const cid=Number($('#bracket-category').value),bs=state.bouts.filter(b=>b.categoryId===cid),area=$('#bracket-area');
 const resultsOpen=Boolean($('#bracket-area .result-controls')?.open);
 if(!bs.length){area.innerHTML='<div class="panel empty">'+(state.tournament.drawSeed!==null?'В этой категории нет участников.':'Сетки появятся после жеребьёвки. Добавьте участников и проверьте категории.')+'</div>';return}
 const main=bs.filter(b=>!b.bronze),max=Math.max(...main.map(b=>b.roundNumber));
 const visibleRounds=Array.from({length:max},(_,i)=>i+1).filter(round=>main.some(b=>b.roundNumber===round&&!b.automatic));
 const ath=id=>state.athletes.find(a=>a.id===id),bronze=bs.find(b=>b.bronze);
 const medals=state.podium?.[cid]||[];
 function match(b){return `<div class="match"><small>${b.bronze?'БОЙ ЗА 3 МЕСТО':'БОЙ '+(b.position+1)}${b.automatic?' · СВОБОДНЫЙ ПРОХОД':''}</small>`+
  [b.redId,b.blueId].map(id=>`<button class="fighter ${b.winnerId&&b.winnerId===id?'winner':''}" data-bout="${b.id}" data-winner="${id||''}" ${b.resolved||!b.redId||!b.blueId?'disabled':''}><span>${esc(ath(id)?.name||(!b.bronze&&(b.roundNumber===1||b.resolved)?'Свободный проход':'Ожидает соперника'))}${ath(id)?'<small>'+esc(ath(id).club)+'</small>':''}</span></button>`).join('')+'</div>'}
 area.innerHTML='<div class="notice">'+(bronze?(count(state.categories.find(c=>c.id===cid))===3?'Три участника: третье место получает единственный проигравший полуфинала, без дополнительного боя.':'Третье место определяется отдельным боем проигравших полуфиналов.'):'При 5–15 участниках присуждаются два третьих места. При 1–2 участниках третьего места нет.')+'</div>'+
 '<section class="podium panel"><div class="panel-title"><h3>Призовые места</h3><a class="text-button" href="/api/tournaments/'+current+'/podium/export">↓ Призёры CSV</a></div>'+
 (medals.length?'<div class="medal-grid">'+medals.map(p=>`<article class="medal medal-${p.place}"><span class="medal-number">${p.place}</span><div><strong>${esc(ath(p.athleteId)?.name)}</strong><small>${esc(ath(p.athleteId)?.club)}</small></div><span>${p.place}-е место</span></article>`).join('')+'</div>':'<p>Призёры появятся после завершения соответствующих боёв.</p>')+
 (bronze&&!bronze.resolved?'<p class="medal-pending">'+(bronze.automatic?'Третье место определится после завершения полуфинала.':'Третье место ожидает результата бронзового боя.')+'</p>':'')+'</section>'+
 poolPreview(cid,bs)+'<details class="result-controls"'+(resultsOpen?' open':'')+'><summary>Внести результаты боёв · карточки</summary><div class="bracket">'+visibleRounds.map(round=>{return `<div class="round"><h3>${round===max?'Финал':round===max-1?'Полуфинал':'Раунд '+round}</h3><div class="round-matches">`+main.filter(b=>b.roundNumber===round&&!b.automatic).map(match).join('')+'</div></div>'}).join('')+'</div>'+
 (bronze&&!bronze.automatic?'<section class="bronze-section panel"><h3>Бой за 3 место</h3><p>Встречаются проигравшие двух полуфиналов.</p><div class="bronze-match">'+match(bronze)+'</div></section>':'')+'</details>';
}
function field(name,label,type='text',attrs='',full=false){return `<label class="${full?'full':''}">${label}<input name="${name}" type="${type}" ${attrs} required></label>`}
function select(name,label,options,full=false){return `<label class="${full?'full':''}">${label}<select name="${name}">${options.map((o,i)=>`<option value="${Array.isArray(o)?o[0]:i}">${Array.isArray(o)?o[1]:o}</option>`).join('')}</select></label>`}
function modal(title,fields,handler){$('#modal-title').textContent=title;$('#modal-fields').innerHTML='<div class="form-grid">'+fields+'</div>';$('#form-error').textContent='';formHandler=handler;$('#modal').showModal()}
function createTournament(){modal('Новый турнир',field('name','Название','text','maxlength="160"',true)+field('eventDate','Дата турнира','date',`value="${new Date().toLocaleDateString('en-CA')}"`)+field('venue','Место проведения','text','maxlength="200"'),async d=>{let t=await api('','POST',d);await list(t.id);switchView('categories')})}
$('#modal-form').onsubmit=async e=>{e.preventDefault();const btn=e.submitter;btn.disabled=true;try{await formHandler(Object.fromEntries(new FormData(e.target)));$('#modal').close()}catch(err){$('#form-error').textContent=err.message}finally{btn.disabled=false}};
$('#close-modal').onclick=$('#cancel-modal').onclick=()=>$('#modal').close();$('#new-tournament').onclick=$('#welcome-create').onclick=createTournament;
$('#add-category').onclick=()=>modal('Новая категория',field('name','Название категории','text','maxlength="160"',true)+select('sex','Пол',[['M','Мужской'],['F','Женский']])+field('minAge','Возраст от, лет','number','min="4" max="100" value="12"')+field('maxAge','Возраст до, лет','number','min="4" max="100" value="13"')+field('minWeight','Вес свыше, кг','number','min="0" max="499" step="0.01" value="0"')+field('maxWeight','Вес до включительно, кг','number','min="1" max="500" step="0.01" value="45"')+select('minBelt','Пояс от',belts)+select('maxBelt','Пояс до',belts),async d=>{['minAge','maxAge','minWeight','maxWeight','minBelt','maxBelt'].forEach(k=>d[k]=Number(d[k]));await api('/'+current+'/categories','POST',d);await load()});
$('#add-athlete').onclick=()=>modal('Новый участник',field('name','Фамилия и имя','text','maxlength="160"',true)+field('club','Клуб / команда','text','maxlength="160"',true)+field('birthDate','Дата рождения','date')+select('sex','Пол',[['M','Мужской'],['F','Женский']])+field('weight','Вес, кг','number','min="1" max="500" step="0.01"')+select('belt','Пояс',belts),async d=>{d.weight=Number(d.weight);d.belt=Number(d.belt);await api('/'+current+'/athletes','POST',d);await load()});
async function draw(){if(state.tournament.drawSeed!==null){switchView('brackets');return}if(!confirm('Провести жеребьёвку? Состав участников и категории будут заблокированы до сброса сеток.'))return;state=await api('/'+current+'/draw','POST',{});view='brackets';render();toast('Жеребьёвка завершена')}
$('#draw').onclick=$('#hero-draw').onclick=()=>safe(draw);$('#reset').onclick=()=>safe(async()=>{if(confirm('Удалить все сетки и результаты боёв? Это действие нельзя отменить.')){state=await api('/'+current+'/draw','DELETE');render()}});
$('#tournament-select').onchange=e=>safe(async()=>{current=Number(e.target.value);await load()});$('#search').oninput=$('#only-unassigned').onchange=renderAthletes;$('#bracket-category').onchange=renderBrackets;$('#print').onclick=()=>{
 const cid=Number($('#bracket-category').value);
 if(!state||!state.bouts.some(b=>b.categoryId===cid)){toast('Сетка этой категории ещё не построена. Сначала проведите жеребьёвку турнира.');return}
 window.location.href='/api/tournaments/'+current+'/categories/'+cid+'/print.pdf';
};$('#export').onclick=()=>{if(!state.bouts.length){toast('Сначала проведите жеребьёвку');return}window.location.href='/api/tournaments/'+current+'/export'};
document.addEventListener('click',e=>{const nav=e.target.closest('[data-view]');if(nav){if(!state){toast('Сначала создайте турнир');return}switchView(nav.dataset.view)}const da=e.target.closest('[data-delete-athlete]'),dc=e.target.closest('[data-delete-category]'),fight=e.target.closest('[data-bout]');if(da||dc)safe(async()=>{if(confirm('Удалить запись?')){await api('/'+current+(da?'/athletes/'+da.dataset.deleteAthlete:'/categories/'+dc.dataset.deleteCategory),'DELETE');await load()}});if(fight&&!fight.disabled)safe(async()=>{const a=state.athletes.find(a=>a.id===Number(fight.dataset.winner));if(confirm('Подтвердить победу: '+a.name+'? Для исправления результата потребуется сбросить всю жеребьёвку.')){state=await api('/'+current+'/bouts/'+fight.dataset.bout+'/winner','POST',{athleteId:a.id});render()}})});
$('#demo').onclick=()=>safe(async()=>{const btn=$('#demo');btn.disabled=true;try{const t=await api('/demo','POST');await list(t.id);switchView('overview');toast('Демонстрационный турнир создан')}finally{btn.disabled=false}});

function renderApplications(){
 if(!$('#application-batches'))return;
 $('#application-batches').innerHTML=applicationBatches.map(b=>`<tr><td>${esc(b.team)}</td><td>${esc(b.sourceName)}</td><td>${b.acceptedCount}</td><td>${esc(new Date(b.createdAt).toLocaleString('ru-RU'))}</td></tr>`).join('')||'<tr><td colspan="4" class="empty">Принятых заявок пока нет.</td></tr>';
}
$('#print-settings').onclick=()=>{
 modal('Настройки печатных пуль',field('tatami','Татами','text',`value="${esc(printSettings.tatami)}" maxlength="8"`)+field('timeSlot','Время / смена','text',`value="${esc(printSettings.timeSlot)}" maxlength="80"`)+field('chiefJudge','Главный судья','text',`value="${esc(printSettings.chiefJudge)}" maxlength="160"`,true)+field('secretary','Главный секретарь','text',`value="${esc(printSettings.secretary)}" maxlength="160"`,true)+`<label class="full"><span><input type="checkbox" name="banner" ${printSettings.banner?'checked':''}> Шапка с логотипами с образца</span></label>`,async d=>{d.banner=Boolean(d.banner);printSettings=await api('/'+current+'/print-settings','PUT',d);renderBrackets();toast('Настройки пуль сохранены')});
 $$('#modal-fields input').forEach(el=>{if(el.name!=='tatami')el.required=false});
};
function poolPreview(cid,bs){
 const main=bs.filter(b=>!b.bronze),max=Math.max(...main.map(b=>b.roundNumber));
 const c=state.categories.find(c=>c.id===cid),ath=id=>state.athletes.find(a=>a.id===id),cfg=printSettings;
 let pages='',pageNumber=0;
 const shorten=(s,n)=>{s=String(s||'');return s.length>n?s.slice(0,n-1)+'…':s};
 for(let first=1;first<=max;first+=4){const last=Math.min(first+3,max),columns=last-first+1,step=Math.min(101,(557-151)/columns);
  for(const root of main.filter(b=>b.roundNumber===last)){
   pageNumber++;let svg=`<svg class="pool-sheet" viewBox="0 0 595 842" role="group" aria-label="Пуля ${esc(c.name)}. Лист ${pageNumber}"><rect width="595" height="842" fill="white"/>`;
   const text=(x,y,size,value,extra='')=>`<text x="${x}" y="${y}" font-size="${size}" ${extra}>${esc(value)}</text>`;
   const line=(x,y,x2,y2)=>`<path d="M${x},${y} L${x2},${y2}" fill="none" stroke="#222" stroke-width=".4"/>`;
   if(cfg.banner)svg+='<image href="/print-banner.png" x="84" y="20" width="491" height="52"/>';else svg+=text(297,55,15,state.tournament.name,'text-anchor="middle"');
   svg+=text(297,84,10,'Турнирная таблица в дисциплине КУМИТЕ','text-anchor="middle" font-weight="700"')+text(297,96,8,c.name,'text-anchor="middle"')+line(18,100,575,100)+text(297,108,8,'ТАТАМИ: '+cfg.tatami+(cfg.timeSlot?' (Дисциплина Кумите '+cfg.timeSlot+')':''),'text-anchor="middle"')+'<rect x="18" y="112" width="557" height="12" fill="#c4c4c4"/>'+text(20,121,7,'Участники: '+count(c));
   const leaf=(id,x,y,w,b,initial)=>{const a=ath(id),ready=!b.resolved&&b.redId&&b.blueId;
    return `<g ${ready?`class="pool-fighter" data-bout="${b.id}" data-winner="${id}" tabindex="0" role="button" aria-label="Победа: ${esc(a?.name)}"`:''}>`+(ready?`<rect x="${x}" y="${y-19}" width="${w}" height="21" fill="transparent"/>`:'')+text(x+1,y-10,8,shorten(a?.name||(initial?'Свободный проход':''),33),'font-weight="600"')+text(x+1,y-2,6,shorten(a?.coach||a?.club||'',42))+line(x,y,x+w,y)+'</g>';
   };
   for(let round=first;round<=last;round++){
    const endpoint=169+(round-first)*step,scale=2**(last-round);
    svg+=text(endpoint-13,121,7,round===max?'Финал':'1/'+2**(max-round));
    for(const b of main.filter(b=>b.roundNumber===round&&Math.floor(b.position/scale)===root.position)){
     if(b.automatic)continue;
     const local=b.position-root.position*scale,center=146+((local+.5)*2**(round-first+1)-.5)*23,offset=2**(round-first)*23/2;
     if(round===first)svg+=leaf(b.redId,18,center-offset,151,b,round===1)+leaf(b.blueId,18,center+offset,151,b,round===1);
     else {
      // Every next-round entry belongs to that bout, including ordinary winners.
      svg+=leaf(b.redId,endpoint-step,center-offset,step,b,false);
      svg+=leaf(b.blueId,endpoint-step,center+offset,step,b,false);
     }
     svg+=line(endpoint,center-offset,endpoint,center+offset)+line(endpoint,center,endpoint+step,center)+text(endpoint-25,center+3,7,cfg.tatami+b.id);
     if(round===max&&b.winnerId)svg+=text(endpoint+3,center-3,7,shorten(ath(b.winnerId)?.name,22));
    }
   }
   let bottom=146+(2**(last-first+1)-1)*23;
   const bronze=bs.find(b=>b.bronze);
   if(last===max&&bronze&&!bronze.automatic){const x=169,y=bottom+13,b=bronze;svg+=text(x+step+23,y-23,7,'Бой за третье место')+leaf(b.redId,x,y,step,b,false)+leaf(b.blueId,x,y+23,step,b,false)+line(x+step,y,x+step,y+23)+line(x+step,y+11.5,x+step*2,y+11.5)+text(x+step-25,y+15,7,cfg.tatami+b.id);if(b.winnerId)svg+=text(x+step+3,y+8,7,shorten(ath(b.winnerId)?.name,22));bottom=y+23;}
   const tableY=bottom+24,medals=state.podium?.[cid]||[],places=!bronze&&count(c)>=5?[1,2,3,3]:[1,2,3],used={};
   svg+=`<rect x="18" y="${tableY}" width="253" height="${11*(places.length+1)}" fill="none" stroke="black" stroke-width=".4"/>`+line(44,tableY,44,tableY+11*(places.length+1))+text(20,tableY+8,7,'Место')+text(47,tableY+8,7,'Победители');
   places.forEach((place,i)=>{const y=tableY+11*(i+1),list=medals.filter(m=>m.place===place),idx=used[place]||0;used[place]=idx+1;svg+=line(18,y,271,y)+text(30,y+8,7,place)+text(47,y+8,7,shorten(ath(list[idx]?.athleteId)?.name,44));});
   const official=tableY+11*(places.length+1)+32;
   svg+=text(20,official,7,'Главный судья')+text(170,official,7,cfg.chiefJudge)+text(20,official+23,7,'Главный секретарь')+text(170,official+23,7,cfg.secretary)+line(95,official+10,155,official+10)+line(95,official+33,155,official+33)+text(18,819,6,state.tournament.name+' · '+state.tournament.eventDate+' · Лист '+pageNumber)+'</svg>';
   pages+=svg;
  }
 }
 return '<div class="pool-preview">'+pages+'</div>';
}
$('#bracket-area').addEventListener('keydown',e=>{if(e.target.matches('.pool-fighter')&&(e.key==='Enter'||e.key===' ')){e.preventDefault();e.target.dispatchEvent(new MouseEvent('click',{bubbles:true}))}});
function importStatus(){
 const selected=$$('#import-rows input[data-select]').filter(x=>x.checked).length,ready=importRows.filter(r=>!r.errors.length).length;
 $('#import-summary').textContent=`Распознано: ${importRows.length}. Подходят: ${ready}. Выбрано: ${selected}.`;
 $('#confirm-import').disabled=!importVerified||selected===0;
}
function showImport(preview){
 importRows=preview.rows;importVerified=true;$('#import-preview').hidden=false;
 $('#import-warnings').hidden=!preview.warnings.length;$('#import-warnings').textContent=preview.warnings.join(' ');
 $('#import-rows').innerHTML=importRows.map((r,i)=>{const e=r.entry;return `<tr data-index="${i}"><td><input data-select type="checkbox" ${r.errors.length?'':'checked'} aria-label="Добавить строку ${e.sourceRow}"></td><td><input data-field="name" value="${esc(e.name)}" maxlength="160" aria-label="ФИО строки ${e.sourceRow}"><small>Строка ${e.sourceRow} · ${esc(e.sportRank)}</small></td><td><input data-field="birthDate" type="date" value="${esc(e.birthDate)}" aria-label="Дата рождения строки ${e.sourceRow}"></td><td><select data-field="sex" aria-label="Пол строки ${e.sourceRow}"><option value="">?</option><option value="M" ${e.sex==='M'?'selected':''}>М</option><option value="F" ${e.sex==='F'?'selected':''}>Ж</option></select></td><td><input data-field="weight" type="number" min="1" max="500" step=".01" value="${e.weight??''}" aria-label="Вес строки ${e.sourceRow}"></td><td><input data-field="kyu" type="number" min="0" max="11" value="${e.kyu??''}" aria-label="Кю строки ${e.sourceRow}"><select data-field="belt" aria-label="Пояс строки ${e.sourceRow}"><option value="">Выберите</option>${belts.map((b,n)=>`<option value="${n}" ${e.belt===n?'selected':''}>${b}</option>`).join('')}</select></td><td><input data-field="club" value="${esc(e.club)}" maxlength="160" aria-label="Команда строки ${e.sourceRow}"><input data-field="coach" value="${esc(e.coach)}" maxlength="160" aria-label="Тренер строки ${e.sourceRow}"></td><td class="row-validation">${r.errors.length?`<span class="import-invalid">${r.errors.map(esc).join('<br>')}</span>`:`<span class="pill">${esc(r.categoryName)}</span>`}</td></tr>`}).join('');
 importStatus();
}
function importEntries(){return $$('#import-rows tr').map((tr,i)=>{const e={...importRows[i].entry};tr.querySelectorAll('[data-field]').forEach(el=>{let value=el.value;if(['weight','kyu','belt'].includes(el.dataset.field))value=value===''?null:Number(value);if(el.dataset.field==='birthDate'&&!value)value=null;e[el.dataset.field]=value});return e});}
async function importApi(path,body){const response=await fetch('/api/tournaments/'+importTournament+'/applications'+path,{method:'POST',headers:body instanceof FormData?{}:{'Content-Type':'application/json'},body:body instanceof FormData?body:JSON.stringify(body)});let result;try{result=await response.json()}catch{throw Error('Не удалось обработать заявку')}if(!response.ok)throw Error(result.message||'Ошибка загрузки заявки');return result;}
async function importAction(fn){$('#import-error').textContent='';try{await fn()}catch(e){$('#import-error').textContent=e.message}}
$('#import-application').onclick=()=>{importTournament=current;importRows=[];importVerified=false;$('#upload-application').reset();$('#import-preview').hidden=$('#import-warnings').hidden=true;$('#import-error').textContent='';$('#confirm-import').disabled=true;$('#import-modal').showModal()};
$('#close-import').onclick=$('#cancel-import').onclick=()=>$('#import-modal').close();
$('#upload-application').onsubmit=e=>{e.preventDefault();importAction(async()=>{const file=$('#application-file').files[0];if(!file)throw Error('Выберите DOCX');if(file.size>10*1024*1024)throw Error('Размер файла не должен превышать 10 МБ');const body=new FormData();body.set('team',$('#application-team').value);body.set('file',file);e.submitter.disabled=true;try{showImport(await importApi('/preview',body))}finally{e.submitter.disabled=false}})};
$('#import-rows').onchange=e=>{if(e.target.matches('[data-select]')){importStatus();return}const tr=e.target.closest('tr'),key=e.target.dataset.field;
 if(key==='kyu'){const k=e.target.value,beltsForKyu=[0,5,5,4,4,3,3,2,2,1,1,0];tr.querySelector('[data-field="belt"]').value=k===''?'':beltsForKyu[Number(k)]??''}
 if(key==='belt')tr.querySelector('[data-field="kyu"]').value='';
 importVerified=false;$('#confirm-import').disabled=true;tr.querySelector('.row-validation').textContent='Данные изменены. Нажмите «Проверить изменения».';
};
$('#select-import-ready').onclick=()=>{$$('#import-rows tr').forEach((tr,i)=>tr.querySelector('[data-select]').checked=!importRows[i].errors.length);importStatus()};
$('#check-import').onclick=()=>importAction(async()=>{const b=$('#check-import');b.disabled=true;try{showImport(await importApi('/check',{entries:importEntries()}))}finally{b.disabled=false}});
$('#confirm-import').onclick=()=>importAction(async()=>{const button=$('#confirm-import');button.disabled=true;try{const entries=importEntries().filter((e,i)=>$$('#import-rows input[data-select]')[i].checked);const result=await importApi('',{sourceName:$('#application-file').files[0]?.name||'Заявка DOCX',team:$('#application-team').value,entries});$('#import-modal').close();await load();switchView('athletes');toast('Добавлено участников: '+result.accepted)}finally{if($('#import-modal').open)importStatus()}});
safe(()=>list());
