import {excluded} from '../_shared/policy.ts';
import postgres from 'npm:postgres@3.4.3'

const sql = postgres(Deno.env.get('SUPABASE_DB_URL')!, { prepare: false, max: 1, idle_timeout: 0, connect_timeout: 10 })
const sourceId = 'galeria-savaria'
const sourceName = 'Galéria Savaria'
const baseUrl = 'https://galeriasavaria.hu'
const GLOBAL_QUERY = '__all__'
const GLOBAL_PAGES_PER_RUN = 6
const KEYWORD_TASKS_PER_RUN = 4

type Rule = { category:string; terms:string[]; base:number; confidence:number; lowMult:number; highMult:number; risk?:string }
const rules: Rule[] = [
  {category:'Modellini',terms:['hot wheels redline','redline hot wheels','matchbox lesney','dinky toys','corgi toys'],base:86,confidence:78,lowMult:2.5,highMult:8},
  {category:'Modellini',terms:['cmc','bbr','autoart','exoto','amalgam','mr collection','frontiart','eidolon','make up','kyosho','minichamps','tomica limited vintage'],base:82,confidence:80,lowMult:2,highMult:5},
  {category:'Modellini',terms:['tomica','schuco','norev','spark model','tekno','solido'],base:72,confidence:68,lowMult:1.7,highMult:3.5},
  {category:'Design',terms:['paolo buffa','fritz hansen','arne jacobsen','jaroslav šmídek','jaroslav smidek','heczendorfer','de sede','selman selmanagic','wittmann','antonin suman'],base:86,confidence:82,lowMult:2.2,highMult:6},
  {category:'Design',terms:['cassina','vitra','knoll','thonet','marcel breuer','hans wegner','finn juhl','charles eames','ray eames','verner panton','kartell'],base:82,confidence:78,lowMult:2,highMult:5},
  {category:'Design',terms:['mid century','mid-century','csehszlovák','skandináv','bauhaus'],base:68,confidence:54,lowMult:1.5,highMult:3},
  {category:'Lampade',terms:['artemide','flos','louis poulsen','charlotte perriand','stilnovo','guzzini','fontana arte','fontanaarte'],base:84,confidence:80,lowMult:2,highMult:5.5},
  {category:'Orologi',terms:['universal geneve','heuer','omega','zenith','longines','jaeger','eterna','vulcain','enicar'],base:82,confidence:76,lowMult:1.8,highMult:4},
  {category:'Orologi',terms:['seiko 6139','seiko 6105','seiko h557','seiko pogue','tissot vintage','certina ds'],base:78,confidence:74,lowMult:1.8,highMult:4},
  {category:'Robot/Giocattoli',terms:['bandai','popy','bullmark','takatoku','nomura','horikawa','yonezawa','masudaya','tin robot','vintage robot'],base:82,confidence:74,lowMult:2,highMult:6},
  {category:'Hi-Fi',terms:['marantz','sansui','luxman','accuphase','nakamichi','thorens','revox','mcintosh','quad 405','technics sp-10'],base:80,confidence:76,lowMult:1.8,highMult:4},
  {category:'Fotocamere',terms:['leica','hasselblad','rolleiflex','contax','mamiya 7','mamiya rz67','pentax 67','nikon f2','nikon f3'],base:82,confidence:78,lowMult:1.8,highMult:4.5},
  {category:'Strumenti',terms:['gibson','fender usa','rickenbacker','martin guitar','moog','roland juno','rhodes piano'],base:80,confidence:72,lowMult:1.7,highMult:4},
  {category:'Computer vintage',terms:['commodore 64','commodore amiga','amiga 500','amiga 1200','apple ii','atari st','sinclair zx','ibm model m'],base:76,confidence:72,lowMult:1.7,highMult:4},
  {category:'Vetro/Ceramica',terms:['venini','murano sommerso','barovier','seguso','zsolnay eosin','herend'],base:80,confidence:70,lowMult:1.8,highMult:4.5},
  {category:'Vetro/Ceramica',terms:['murano','zsolnay','kosta boda','iittala'],base:70,confidence:58,lowMult:1.5,highMult:3,risk:'medio-alto: attribuzione da verificare'},
  {category:'Auto/Ricambi',terms:['recaro','bbs rs','bbs rm','momo prototipo','momo montecarlo','nardi volante','porsche fuchs'],base:76,confidence:70,lowMult:1.7,highMult:3.5},
  {category:'Lusso',terms:['hermès','hermes','chanel vintage','louis vuitton vintage','cartier','gucci vintage','prada vintage'],base:72,confidence:46,lowMult:1.5,highMult:3.5,risk:'alto: autenticità obbligatoria'}
]

const genericCategory: Record<string,string> = {
  'régi fotel':'Design','retro fotel':'Design','design fotel':'Design','régi szék':'Design','régi lámpa':'Lampade','retro lámpa':'Lampade','asztali lámpa':'Lampade',
  'régi karóra':'Orologi','vintage óra':'Orologi','régi játék':'Giocattoli','játék robot':'Robot/Giocattoli','japán robot':'Robot/Giocattoli',
  'régi kisautó':'Modellini','modell autó':'Modellini','modellautó':'Modellini','régi erősítő':'Hi-Fi','vintage hifi':'Hi-Fi','régi fényképezőgép':'Fotocamere',
  'régi hangszer':'Strumenti','régi számítógép':'Computer vintage','murano':'Vetro/Ceramica','zsolnay':'Vetro/Ceramica','herend':'Vetro/Ceramica'
}

type Listing={url:string;title:string;price:number;context:string;sourceItemId:string|null;isAuction:boolean;imageUrls:string[]}
type Cursor={source_id:string;query:string;current_page:number;last_page:number|null;cycle_count:number}

function validApiKey(req:Request){
  const supplied=req.headers.get('apikey')??''
  try{const p=JSON.parse(Deno.env.get('SUPABASE_PUBLISHABLE_KEYS')??'{}');return Object.values(p).some(v=>typeof v==='string'&&v===supplied)}catch{return false}
}
function decodeEntities(input:string){const named:Record<string,string>={amp:'&',quot:'"',apos:"'",lt:'<',gt:'>',nbsp:' '};return input.replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(Number(n))).replace(/&#x([0-9a-f]+);/gi,(_,n)=>String.fromCodePoint(parseInt(n,16))).replace(/&([a-z]+);/gi,(m,n)=>named[n.toLowerCase()]??m)}
function plain(input:string){return decodeEntities(input.replace(/<script[\s\S]*?<\/script>/gi,' ').replace(/<style[\s\S]*?<\/style>/gi,' ').replace(/<[^>]+>/g,' ')).replace(/\s+/g,' ').trim()}
function parsePrice(text:string){const m=text.match(/([0-9][0-9.\s\u00a0]{0,18})\s*HUF\b/i);if(!m)return null;const n=Number(m[1].replace(/\D/g,''));return Number.isFinite(n)&&n>0?n:null}
function auctionFlag(text:string,title=''){const l=`${title} ${text}`.toLocaleLowerCase('hu-HU');return /lejárat:|\blicit\b|aukció|1\s*ft-ról|1\s*ft-rol/.test(l)}
function normalizeUrl(url:string){try{const u=new URL(url,baseUrl);u.pathname=u.pathname.replace(/\/{2,}/g,'/');return u.toString()}catch{return url}}
function imagesOf(x:any):string[]{const src=x?.image;const arr=Array.isArray(src)?src:[src];const out:string[]=[];for(const v of arr){if(typeof v==='string')out.push(v);else if(v&&typeof v==='object'&&typeof v.url==='string')out.push(v.url)}return [...new Set(out.map(normalizeUrl))].slice(0,12)}

function identify(title:string,context:string,query:string,price:number,isAuction:boolean){
  const hay=`${title} ${context}`.toLocaleLowerCase('hu-HU')
  const matches:{rule:Rule;term:string}[]=[]
  for(const rule of rules)for(const term of rule.terms)if(hay.includes(term.toLocaleLowerCase('hu-HU')))matches.push({rule,term})
  const best=matches.sort((a,b)=>b.rule.base-a.rule.base)[0]
  let score:number,confidence:number,category:string,low:number|null=null,high:number|null=null
  let risk='medio: serve verifica modello/condizioni',valuationStatus='needs_visual_identification'
  if(best){score=best.rule.base;confidence=best.rule.confidence;category=best.rule.category;if(!isAuction){low=Math.round(price*best.rule.lowMult);high=Math.round(price*best.rule.highMult)}risk=best.rule.risk??'medio: pre-stima, verificare comparabili venduti';valuationStatus='pre_estimate_keyword'}
  else{category=query===GLOBAL_QUERY?'360° / Da identificare':(genericCategory[query]??'360° / Da identificare');score=42;if(price<=7500)score+=20;else if(price<=15000)score+=16;else if(price<=30000)score+=10;else if(price<=50000)score+=5;confidence=24;risk='alto: candidato anonimo, serve identificazione visiva'}
  if(price<=15000&&best)score+=6;else if(price<=30000&&best)score+=3
  if(price>500000)score-=8
  if(isAuction){score-=8;low=null;high=null;risk='alto: asta in corso, il prezzo mostrato è un’offerta corrente e può salire';valuationStatus='auction_needs_comps'}
  score=Math.max(0,Math.min(100,score))
  const profit=low==null?null:low-price,roi=profit==null?null:Math.round((profit/Math.max(price,1))*100)
  const verdict=score>=86?'PRE-STIMA MOLTO FORTE — VERIFICARE':score>=78?'PRE-STIMA FORTE — SERVONO COMPARABILI':score>=68?'DA CONTROLLARE':'CODA VISIVA'
  return{category,score,confidence,low,high,profit,roi,risk,verdict,valuationStatus,matchedTerms:[...new Set(matches.map(x=>x.term))].slice(0,12)}
}

function collectProducts(node:any,out:Listing[]){
  if(Array.isArray(node)){for(const x of node)collectProducts(x,out);return}
  if(!node||typeof node!=='object')return
  const type=node['@type']
  if((type==='Product'||(Array.isArray(type)&&type.includes('Product')))&&node.name&&node.offers){
    const offers=Array.isArray(node.offers)?node.offers[0]:node.offers
    const price=Number(String(offers?.price??'').replace(/[^0-9.]/g,''));const url=normalizeUrl(String(offers?.url??''))
    if(Number.isFinite(price)&&price>0&&url){const idm=url.match(/\/reszletek\/[^/]+\/(\d+)\//);out.push({title:String(node.name),price:Math.round(price),url,sourceItemId:idm?.[1]??null,context:'',isAuction:auctionFlag('',String(node.name)),imageUrls:imagesOf(node)})}
  }
  for(const [k,v] of Object.entries(node))if(k!=='image')collectProducts(v,out)
}
function extractJsonLd(html:string){const out:Listing[]=[];const re=/<script[^>]+type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi;let m:RegExpExecArray|null;while((m=re.exec(html))!==null){try{collectProducts(JSON.parse(m[1]),out)}catch{}}const u=new Map<string,Listing>();for(const x of out)if(!u.has(x.url))u.set(x.url,x);return[...u.values()]}
function extractAnchorContext(html:string){
  const re=/<a\b[^>]*href=["'](\/termekek\/reszletek\/[^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi
  const a:{index:number;end:number;href:string;title:string}[]=[];let m:RegExpExecArray|null
  while((m=re.exec(html))!==null)a.push({index:m.index,end:re.lastIndex,href:m[1],title:plain(m[2])})
  const out=new Map<string,{context:string;price:number|null;title:string;isAuction:boolean}>()
  for(let i=0;i<a.length;i++){const x=a[i];if(!x.title||x.title.length<4)continue;const next=a.slice(i+1).find(y=>y.href!==x.href);const end=next?next.index:Math.min(html.length,x.end+4000);const context=plain(html.slice(x.end,end)).slice(0,1500);const url=normalizeUrl(x.href);out.set(url,{context,price:parsePrice(context),title:x.title,isAuction:auctionFlag(context,x.title)})}
  return out
}
function extractListings(html:string){
  const ld=extractJsonLd(html),ctx=extractAnchorContext(html),merged=new Map<string,Listing>()
  for(const x of ld){const c=ctx.get(x.url);merged.set(x.url,{...x,title:x.title||c?.title||'',price:x.price||c?.price||0,context:c?.context??'',isAuction:x.isAuction||c?.isAuction||false})}
  for(const [url,c] of ctx){if(merged.has(url)||!c.price)continue;const idm=url.match(/\/reszletek\/[^/]+\/(\d+)\//);merged.set(url,{url,title:c.title,price:c.price,context:c.context,sourceItemId:idm?.[1]??null,isAuction:c.isAuction,imageUrls:[]})}
  return[...merged.values()].filter(x=>x.price>0).slice(0,80)
}
function extractLastPage(html:string,current:number){const m=html.match(/<strong>\s*\d+\.\s*oldal\s*\/\s*(\d+)\s*<\/strong>/i);const n=m?Number(m[1]):current;return Number.isFinite(n)&&n>=1?n:current}
function pageUrl(query:string,page:number){if(query===GLOBAL_QUERY)return page<=1?`${baseUrl}/termekek?limit=60`:`${baseUrl}/termekek/oldal/${page}/?limit=60`;return page<=1?`${baseUrl}/kereses/?keyword=${encodeURIComponent(query)}&limit=60`:`${baseUrl}/kereses/oldal/${page}/?keyword=${encodeURIComponent(query)}&limit=60`}
async function fetchPage(query:string,page:number){const res=await fetch(pageUrl(query,page),{headers:{'user-agent':'AffareRadar/0.5 (+deal-research; respectful rolling backfill)','accept-language':'hu-HU,hu;q=0.9,en;q=0.7'},signal:AbortSignal.timeout(15000)});if(!res.ok)throw new Error(`HTTP ${res.status}`);const html=await res.text();return{listings:extractListings(html),lastPage:extractLastPage(html,page)}}

async function saveListing(item:Listing,query:string){
  if(excluded({title:item.title,description:item.context}))return false;const info=identify(item.title,item.context,query,item.price,item.isAuction);if(info.score<58)return false
  const raw={query:query===GLOBAL_QUERY?'catalogo-generale':query,context:item.context,scanVersion:'0.5',isAuction:item.isAuction,imageCount:item.imageUrls.length}
  await sql`
    insert into public.ar_deals(source_id,source_name,source_item_id,url,title,price_huf,category,score,confidence,estimated_low_huf,estimated_high_huf,conservative_profit_huf,roi,risk,verdict,valuation_status,matched_terms,query,found_at,last_seen_at,active,raw,updated_at,is_auction,image_urls)
    values(${sourceId},${sourceName},${item.sourceItemId},${item.url},${item.title},${item.price},${info.category},${info.score},${info.confidence},${info.low},${info.high},${info.profit},${info.roi},${info.risk},${info.verdict},${info.valuationStatus},${sql.array(info.matchedTerms)},${query},now(),now(),true,${sql.json(raw)},now(),${item.isAuction},${sql.array(item.imageUrls)})
    on conflict(url) do update set
      title=excluded.title,price_huf=excluded.price_huf,category=excluded.category,
      score=case when public.ar_deals.valuation_checked_at is not null or public.ar_deals.authenticity_checked_at is not null then public.ar_deals.score else excluded.score end,
      confidence=case when public.ar_deals.valuation_checked_at is not null or public.ar_deals.authenticity_checked_at is not null then public.ar_deals.confidence else excluded.confidence end,
      estimated_low_huf=case when public.ar_deals.valuation_checked_at is not null then public.ar_deals.estimated_low_huf else excluded.estimated_low_huf end,
      estimated_high_huf=case when public.ar_deals.valuation_checked_at is not null then public.ar_deals.estimated_high_huf else excluded.estimated_high_huf end,
      conservative_profit_huf=case when public.ar_deals.valuation_checked_at is not null then public.ar_deals.conservative_profit_huf else excluded.conservative_profit_huf end,
      roi=case when public.ar_deals.valuation_checked_at is not null then public.ar_deals.roi else excluded.roi end,
      risk=case when public.ar_deals.valuation_checked_at is not null or public.ar_deals.authenticity_checked_at is not null then public.ar_deals.risk else excluded.risk end,
      verdict=case when public.ar_deals.valuation_checked_at is not null or public.ar_deals.authenticity_checked_at is not null then public.ar_deals.verdict else excluded.verdict end,
      valuation_status=case when public.ar_deals.valuation_checked_at is not null then public.ar_deals.valuation_status else excluded.valuation_status end,
      matched_terms=excluded.matched_terms,query=excluded.query,last_seen_at=now(),active=true,raw=(case when jsonb_typeof(public.ar_deals.raw)='object' then public.ar_deals.raw else '{}'::jsonb end)||excluded.raw,updated_at=now(),is_auction=excluded.is_auction,
      image_urls=case when cardinality(excluded.image_urls)>0 then excluded.image_urls else public.ar_deals.image_urls end
  `
  return true
}

async function advanceCursor(c:Cursor,page:number,lastPage:number,found:number,error:string|null){
  const terminal=page>=lastPage
  await sql`update public.ar_scan_cursors set current_page=${terminal?1:page+1},last_page=${lastPage},cycle_count=cycle_count+${terminal?1:0},last_scanned_at=now(),cycle_completed_at=case when ${terminal} then now() else cycle_completed_at end,last_found_count=${found},last_error=${error},updated_at=now() where source_id=${c.source_id} and query=${c.query}`
}

Deno.serve(async(req:Request)=>{
  if(req.method!=='POST')return Response.json({error:'POST required'},{status:405})
  if(!validApiKey(req))return Response.json({error:'unauthorized'},{status:401})
  const [lock]=await sql`select pg_try_advisory_lock(hashtext('affareradar-scan')) as locked`;if(!lock?.locked)return Response.json({status:'skipped',reason:'scan already running'})
  let runId:any=null
  try{
    const [run]=await sql`insert into public.ar_scan_runs(status,source_count,meta) values('running',1,${JSON.stringify({trigger:'edge',version:'0.5',mode:'rolling-backfill'})}::jsonb) returning id`;runId=run.id
    const [globalCursor]=await sql<Cursor[]>`select source_id,query,current_page,last_page,cycle_count from public.ar_scan_cursors where source_id=${sourceId} and query=${GLOBAL_QUERY} limit 1`
    const keywordCursors=await sql<Cursor[]>`select source_id,query,current_page,last_page,cycle_count from public.ar_scan_cursors where source_id=${sourceId} and query<>${GLOBAL_QUERY} and lower(query) !~ '(herend|zsolnay|porcel[aá]n|ker[aá]mia|terracotta)' order by last_scanned_at nulls first,query limit ${KEYWORD_TASKS_PER_RUN}`
    const tasks:{cursor:Cursor;page:number}[]=[]
    if(globalCursor){for(let i=0;i<GLOBAL_PAGES_PER_RUN;i++){let p=globalCursor.current_page+i;if(globalCursor.last_page&&p>globalCursor.last_page)p=((p-1)%globalCursor.last_page)+1;tasks.push({cursor:globalCursor,page:p})}}
    for(const c of keywordCursors)tasks.push({cursor:c,page:c.current_page})
    let found=0,candidates=0;const errors:string[]=[],taskMeta:any[]=[]
    let globalLast=globalCursor?.last_page??null,globalLastPageProcessed=globalCursor?.current_page??1,globalFound=0
    for(const task of tasks){
      const {cursor,page}=task
      try{
        const r=await fetchPage(cursor.query,page);found+=r.listings.length;let kept=0
        for(const item of r.listings)if(await saveListing(item,cursor.query)){kept++;candidates++}
        taskMeta.push({query:cursor.query,page,lastPage:r.lastPage,found:r.listings.length,candidates:kept})
        if(cursor.query===GLOBAL_QUERY){globalLast=r.lastPage;globalLastPageProcessed=page;globalFound+=r.listings.length}else await advanceCursor(cursor,page,r.lastPage,r.listings.length,null)
      }catch(err){const msg=err instanceof Error?err.message:String(err);errors.push(`${cursor.query} p${page}: ${msg}`);taskMeta.push({query:cursor.query,page,error:msg});if(cursor.query!==GLOBAL_QUERY)await sql`update public.ar_scan_cursors set last_scanned_at=now(),last_error=${msg.slice(0,1000)},updated_at=now() where source_id=${cursor.source_id} and query=${cursor.query}`}
      await new Promise(r=>setTimeout(r,180))
    }
    if(globalCursor&&globalLast){const terminal=globalLastPageProcessed>=globalLast;await sql`update public.ar_scan_cursors set current_page=${terminal?1:globalLastPageProcessed+1},last_page=${globalLast},cycle_count=cycle_count+${terminal?1:0},last_scanned_at=now(),cycle_completed_at=case when ${terminal} then now() else cycle_completed_at end,last_found_count=${globalFound},last_error=${errors.some(x=>x.startsWith(GLOBAL_QUERY))?'partial error':null},updated_at=now() where source_id=${sourceId} and query=${GLOBAL_QUERY}`}
    const status=errors.length===0?'success':errors.length<tasks.length?'partial':'failed'
    await sql`update public.ar_scan_runs set finished_at=now(),status=${status},found_count=${found},candidate_count=${candidates},error=${errors.length?errors.join(' | ').slice(0,4000):null},meta=${JSON.stringify({tasks:taskMeta,errorCount:errors.length,version:'0.5',mode:'rolling-backfill'})}::jsonb where id=${runId}`
    if(status==='failed')await sql`update public.ar_sources set last_error=${errors.join(' | ').slice(0,4000)},updated_at=now() where id=${sourceId}`
    else await sql`update public.ar_sources set last_success_at=now(),last_error=${errors.length?errors.join(' | ').slice(0,4000):null},updated_at=now() where id=${sourceId}`
    return Response.json({status,runId:String(runId),mode:'rolling-backfill',pages:tasks.length,found,candidates,errors,progress:taskMeta})
  }catch(err){const msg=err instanceof Error?err.message:String(err);if(runId)await sql`update public.ar_scan_runs set finished_at=now(),status='failed',error=${msg.slice(0,4000)} where id=${runId}`;return Response.json({status:'failed',error:msg},{status:500})}
  finally{await sql`select pg_advisory_unlock(hashtext('affareradar-scan'))`}
})

