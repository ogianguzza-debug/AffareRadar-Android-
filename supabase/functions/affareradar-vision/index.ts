import {createRemoteJWKSet, jwtVerify} from 'npm:jose@5.9.6';
import {sql, response} from '../_shared/db.ts';
import {number, strings, excluded} from '../_shared/policy.ts';
const keys = createRemoteJWKSet(new URL('https://token.actions.githubusercontent.com/.well-known/jwks'));
const REPO = 'ogianguzza-debug/AffareRadar-Android-';
async function authorize(req: Request) {
  const token = (req.headers.get('Authorization') ?? '').replace(/^Bearer /i, '');
  const {payload:p} = await jwtVerify(token, keys, {issuer:'https://token.actions.githubusercontent.com', audience:'affareradar-vision', algorithms:['RS256']});
  if (String(p.repository_id) !== '1364267371' || String(p.repository_owner_id) !== '315449255' ||
      p.repository !== REPO || p.ref !== 'refs/heads/main' ||
      p.workflow_ref !== `${REPO}/.github/workflows/review-deals.yml@refs/heads/main` ||
      !['schedule','push','workflow_dispatch'].includes(String(p.event_name))) throw new Error('workflow not allowed');
  return String(p.run_id);
}
Deno.serve(async (req: Request) => {
  if (req.method !== 'POST') return response({error:'POST required'}, 405);
  let runId: string;
  try { runId = await authorize(req); } catch { return response({error:'unauthorized_worker'},401); }
  let body: any;
  try { body = await req.json(); } catch { return response({error:'invalid_json'},400); }
  if (body.event === 'queue') {
    const rows = await sql.begin(async tx => {
      return await tx`update public.ar_deals set vision_status='processing',
        vision_raw=${sql.json({runId, claimedAt:new Date().toISOString()})}, updated_at=now()
        where id in (select id from public.ar_deals where active=true and cardinality(image_urls)>0
          and score>=58 and price_huf between 500 and 250000
          and (vision_status is null or vision_status='pending' or (vision_status in ('processing','error') and updated_at<now()-interval '2 hours'))
          and lower(coalesce(title,'')||' '||coalesce(description,'')) !~ '(parfum|perfume|fragrance|herend|zsolnay|porcel[aá]n|ker[aá]mia|ceramic|terracotta|holl[oó]h[aá]za|aquincum|drasche)'
          order by score desc,found_at desc for update skip locked limit 4)
        returning id,title,description,price_huf,url,image_urls,category`;
    });
    return response({status:'success', paidAi:false, deals:rows.filter(d => !excluded(d))});
  }
  if (!/^[0-9a-f-]{36}$/i.test(String(body.id))) return response({error:'invalid_id'},400);
  const [d] = await sql`select id,vision_raw from public.ar_deals where id=${body.id}`;
  if (!d || d.vision_raw?.runId !== runId) return response({error:'unclaimed_deal'},409);
  if (body.event === 'unavailable') {
    if (![404,410].includes(body.httpStatus)) return response({error:'invalid_unavailable_status'},400);
    await sql`update public.ar_deals set active=false,vision_status='unavailable',vision_checked_at=now(),updated_at=now() where id=${body.id}`;
    return response({status:'success'});
  }
  if (body.event === 'error') {
    await sql`update public.ar_deals set vision_status='error',vision_raw=${sql.json({runId,error:String(body.error).slice(0,300)})},updated_at=now() where id=${body.id}`;
    return response({status:'recorded'});
  }
  const result = body.result;
  const photos = strings(body.photoDigests,4).filter(s => /^[a-f0-9]{64}$/.test(s));
  if (body.event !== 'result' || !result || !photos.length) return response({error:'photo_evidence_required'},400);
  const confidence = Math.max(0,Math.min(100,number(result.confidence)));
  const identification = String(result.identification ?? 'Non identificato').slice(0,1000);
  const query = String(result.comp_query ?? '').replace(/[^\p{L}\p{N} .'-]/gu,' ').trim().slice(0,120);
  const red = strings(result.red_flags), missing = strings(result.missing_photos);
  const reason = String(result.reason ?? '').slice(0,1500);
  const evidence = {runId, model:'qwen3-vl:2b', paidAi:false, photoDigests:photos, result};
  await sql`update public.ar_deals set vision_status='checked',vision_checked_at=now(),
    vision_model='qwen3-vl:2b-local',vision_confidence=${confidence},vision_score=${confidence},
    vision_identification=${identification},vision_reasons=${sql.array([reason])},
    vision_red_flags=${sql.array(red)},vision_missing_photos=${sql.array(missing)},
    vision_positive_signs=${sql.array(strings(result.positive_signs))},vision_raw=${sql.json(evidence)},
    comp_query=${query || null},valuation_checked_at=null,
    authenticity_status=${red.length ? 'needs_visual_review' : 'visual_reviewed_not_certified'},
    authenticity_risk='Esame fotografico automatico; autenticità non certificata.',
    authenticity_requires_vision=false,updated_at=now() where id=${body.id}`;
  return response({status:'success',id:body.id,paidAi:false,confidence});
});
