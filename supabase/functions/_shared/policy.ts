export const EXCLUDED = /parfum|perfume|fragrance|herend|herendi|hollóháza|hollohaza|zsolnay|aquincum|drasche|porcel[aá]n|porcelain|ker[aá]mia|ceramic|terracotta|terrakotta|stoneware|earthenware|pottery/i;
export function number(value: unknown): number {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
}
export function object(value: any): Record<string, any> {
  for (let i = 0; i < 2 && typeof value === 'string'; i++) {
    try { value = JSON.parse(value); } catch { return {}; }
  }
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}
export function strings(value: any, max = 10): string[] {
  return Array.isArray(value) ? value.filter(x => typeof x === 'string').map(x => x.slice(0, 500)).slice(0, max) : [];
}
export function excluded(deal: any): boolean {
  return EXCLUDED.test(`${deal.title ?? ''} ${deal.description ?? ''}`) || /ESCLUSO/.test(deal.verdict ?? '');
}
export function alertEligible(d: any): boolean {
  const evidence = object(d.raw).valueEvidence;
  return d.active !== false && !excluded(d) && !d.is_auction &&
    d.vision_status === 'checked' && number(d.vision_confidence) >= 75 &&
    strings(d.vision_red_flags).length === 0 && strings(d.vision_missing_photos).length === 0 &&
    !['declared_replica', 'high_risk', 'listing_unavailable'].includes(d.authenticity_status) &&
    number(d.sold_comp_count) >= 3 && number(d.conservative_profit_huf) >= 15000 && number(d.score) >= 75 &&
    number(d.estimated_low_huf) > 0 && number(evidence?.askingHuf) === number(d.price_huf) &&
    Array.isArray(evidence?.comparables) && evidence.comparables.length >= 3;
}
export function lane(d: any): string {
  const text = `${d.title ?? ''} ${d.category ?? ''}`;
  const fashion = /gucci|prada|cucinelli|burberry|bottega|miu miu|laurent|loro piana|moda|lusso|luxury|balenciaga|versace|marant|westwood/i.test(text);
  const collection = /design|lamp|lámp|fotel|chair|robot|játék|toy|modell|óra|watch|audio|speaker|hangfal|vintage|retro|collezion|space age/i.test(text);
  return fashion && collection ? 'both' : fashion ? 'resale' : collection ? 'collection' : 'watch';
}
export function clientDeal(d: any) {
  const qualified = alertEligible(d);
  const evidence=object(d.raw).valueEvidence;
  return {
    ...d, raw: undefined, vision_raw: undefined,
    priceHuf: number(d.price_huf), sourceId: d.source_id, sourceName: d.source_name,
    imageUrls: strings(d.image_urls, 16), prudentValueHuf: number(d.estimated_low_huf),
    estimatedValueHuf: number(d.estimated_low_huf), estimatedHighHuf: number(d.estimated_high_huf),
    netProfitHuf: number(d.conservative_profit_huf), mode: lane(d), alertEligible: qualified,
    reviewStatus: qualified ? 'opportunity' : d.vision_status === 'checked' ? 'reviewed' : 'pending',
    comparables: object(d.raw).valueEvidence?.comparables ?? [],
    valuationCurrent: number(evidence?.askingHuf)===number(d.price_huf) && number(d.price_huf)>0 && Array.isArray(evidence?.comparables) && evidence.comparables.length>=3,
    verificationNote: qualified ? 'Foto e comparabili esaminati; autenticità non certificata.' :
      d.vision_status === 'checked' ? 'Foto esaminate: valore, dettagli o comparabili ancora da verificare.' : 'Candidato preliminare: analisi fotografica in attesa.',
  };
}
