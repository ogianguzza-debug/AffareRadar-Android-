import {sql, publicKey, response, headers} from '../_shared/db.ts';
import {clientDeal, excluded} from '../_shared/policy.ts';

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', {headers});
  if (!publicKey(req)) return response({error:'unauthorized'}, 401);
  if (req.method !== 'GET') return response({error:'GET required'}, 405);
  try {
    const [reviewed, priced, preliminary, sources, signals, vision] = await Promise.all([
      sql`select * from public.ar_deals where active=true and vision_status='checked' order by vision_checked_at desc limit 80`,
      sql`select * from public.ar_deals where active=true and sold_comp_count>=3 and conservative_profit_huf>=15000 and score>=75 order by conservative_profit_huf desc limit 80`,
      sql`select * from public.ar_deals where active=true and score>=68 and cardinality(image_urls)>0 and vision_checked_at is null and lower(coalesce(title,'')||' '||coalesce(description,'')) !~ '(parfum|perfume|fragrance|herend|zsolnay|porcel[aá]n|ker[aá]mia|ceramic|terracotta|holl[oó]h[aá]za|aquincum|drasche)' order by found_at desc limit 24`,
      sql`select id,name,enabled,mode,connector_status,connector_note,last_success_at,last_error,updated_at from public.ar_sources order by enabled desc,name`,
      sql`select source_id,resolution_status,count(*)::int as count from public.ar_marketplace_signals group by source_id,resolution_status`,
      sql`select max(vision_checked_at) as last_review_at from public.ar_deals where active=true and vision_status='checked'`,
    ]);
    const seen = new Set<string>();
    const deals = [...reviewed, ...priced].filter(d => !excluded(d) && !seen.has(d.id) && !!seen.add(d.id)).map(clientDeal);
    const candidates = preliminary.filter(d => !excluded(d) && !seen.has(d.id) && !!seen.add(d.id)).map(clientDeal);
    return response({version:'2.0', generatedAt:new Date().toISOString(), filterMode:'evidence-first',
      deals, candidates, health:{sources, signals, lastVisionAt:vision[0]?.last_review_at ?? null,
        visionMode:'open-source-github-runner', paidAi:false, alerts:deals.filter(d => d.alertEligible).length},
      rules:{ceramics:false, minimumProfitHuf:15000, minimumSoldComparables:3, photoReviewRequired:true}});
  } catch (error) {
    console.error('feed failure', error instanceof Error ? error.message : String(error));
    return response({error:'feed_error', deals:[], candidates:[]}, 500);
  }
});
